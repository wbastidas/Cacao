package ec.cacaotrace.ia

import android.content.Context
import android.content.res.AssetFileDescriptor
import ec.cacaotrace.nucleo.ia.GranoDetectado
import ec.cacaotrace.nucleo.ia.ModeloNoDisponible
import ec.cacaotrace.nucleo.ia.MotivoSinModelo
import ec.cacaotrace.nucleo.ia.ResultadoClasificacion
import ec.cacaotrace.nucleo.ia.ResultadoDeteccion
import ec.cacaotrace.nucleo.ia.leerCajasYolo
import ec.cacaotrace.nucleo.ia.suprimirSolapes
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.tensorflow.lite.Interpreter

/**
 * Carga y ejecuta los modelos TFLite dentro del teléfono (RF-IA-01).
 *
 * Principio de diseño: **si no hay modelo, la app no falla**. Muestra "modelo
 * no instalado" y el usuario sigue trabajando con el registro y el conteo
 * manual. Eso es deliberado: la ERS prevé arrancar así mientras los modelos
 * alcanzan sus metas de la §8.1, y significa que la app es útil desde el
 * primer día aunque todavía no existan los `.tflite`.
 *
 * La lógica que se puede probar sin un modelo —umbral, conteo, supresión de
 * solapamientos— vive en `:nucleo`. Aquí solo queda lo que necesita Android.
 */
class ServicioModelos(
    private val contexto: Context,
    private val rutaBase: String = "modelos",
) {
    private val cargados = mutableMapOf<String, ModeloCargado>()
    private val fallidos = mutableMapOf<String, ModeloNoDisponible>()
    private val json = Json { ignoreUnknownKeys = true }

    /** ¿Hay modelo instalado para esta tarea? */
    fun estaDisponible(tarea: String): Boolean = cargados.containsKey(tarea)

    /** Por qué no está, si no está. */
    fun porQueNoEstaDisponible(tarea: String): ModeloNoDisponible? = fallidos[tarea]

    fun versionDe(tarea: String): String? = cargados[tarea]?.version

    fun clasesDe(tarea: String): List<String> = cargados[tarea]?.clases.orEmpty()

    fun umbralDe(tarea: String): Double = cargados[tarea]?.umbralConfianza ?: 0.6

    /**
     * Intenta cargar todos los modelos. Nunca lanza: los que falten quedan
     * registrados como no disponibles y la app arranca igual.
     */
    suspend fun cargarTodos() {
        TAREAS.keys.forEach { cargar(it) }
    }

    /** Carga un modelo. Devuelve false si no está o no se pudo abrir. */
    suspend fun cargar(tarea: String): Boolean = withContext(Dispatchers.IO) {
        if (cargados.containsKey(tarea)) return@withContext true
        val nombreVisible = TAREAS[tarea] ?: tarea

        val metadatosTexto = runCatching {
            contexto.assets.open("$rutaBase/$tarea/metadatos.json")
                .bufferedReader().use { it.readText() }
        }.getOrElse {
            fallidos[tarea] = ModeloNoDisponible(nombreVisible, MotivoSinModelo.NO_INSTALADO)
            return@withContext false
        }

        runCatching {
            val meta = json.parseToJsonElement(metadatosTexto).jsonObject
            val clases = contexto.assets.open("$rutaBase/$tarea/etiquetas.txt")
                .bufferedReader().use { lector ->
                    lector.readLines().map { it.trim() }.filter { it.isNotEmpty() }
                }

            val lado = (meta["entrada"]?.jsonObject?.get("alto")?.jsonPrimitive?.content
                ?: meta["entrada"]?.jsonPrimitive?.content)?.toIntOrNull() ?: 224

            cargados[tarea] = ModeloCargado(
                tarea = tarea,
                interprete = Interpreter(
                    mapearModelo("$rutaBase/$tarea/modelo.tflite"),
                    Interpreter.Options().apply {
                        // Cuatro hilos: en un teléfono de gama media da el
                        // mejor compromiso entre tiempo de respuesta (RNF-02)
                        // y consumo de batería (RNF-04).
                        numThreads = 4
                    },
                ),
                clases = clases,
                version = meta["version"]?.jsonPrimitive?.content ?: "desconocida",
                umbralConfianza = meta["umbral_confianza"]?.jsonPrimitive?.content
                    ?.toDoubleOrNull() ?: 0.6,
                ladoEntrada = lado,
            )
            fallidos.remove(tarea)
            true
        }.getOrElse { e ->
            fallidos[tarea] = ModeloNoDisponible(
                nombreVisible,
                MotivoSinModelo.ERROR_AL_CARGAR,
                e.message.orEmpty(),
            )
            false
        }
    }

    /**
     * Mapea el `.tflite` en memoria sin copiarlo.
     *
     * Por eso el `build.gradle.kts` declara `noCompress += "tflite"`: un
     * archivo comprimido dentro del APK no se puede mapear directamente.
     */
    private fun mapearModelo(ruta: String): MappedByteBuffer {
        val descriptor: AssetFileDescriptor = contexto.assets.openFd(ruta)
        FileInputStream(descriptor.fileDescriptor).use { entrada ->
            return entrada.channel.map(
                FileChannel.MapMode.READ_ONLY,
                descriptor.startOffset,
                descriptor.declaredLength,
            )
        }
    }

    /**
     * Clasifica una imagen ya redimensionada al lado que pide el modelo.
     *
     * [pixeles] son valores 0-255 en orden RGB. El modelo normaliza por dentro,
     * así que la app no tiene que replicar ninguna fórmula de preprocesado
     * (§8.2 de la ERS). Ese contrato es lo que permite cambiar de arquitectura
     * sin tocar la app.
     */
    suspend fun clasificar(
        tarea: String,
        pixeles: ByteArray,
        umbralPersonalizado: Double? = null,
    ): ResultadoClasificacion = withContext(Dispatchers.Default) {
        val modelo = cargados[tarea] ?: throw (
            fallidos[tarea] ?: ModeloNoDisponible(
                TAREAS[tarea] ?: tarea,
                MotivoSinModelo.NO_INSTALADO,
            )
            )

        val lado = modelo.ladoEntrada
        val esperado = lado * lado * 3
        require(pixeles.size == esperado) {
            "La imagen debe venir en ${lado}×$lado píxeles RGB ($esperado valores); " +
                "llegaron ${pixeles.size}."
        }

        val inicio = System.currentTimeMillis()
        val entrada = Array(1) {
            Array(lado) { y ->
                Array(lado) { x ->
                    val i = (y * lado + x) * 3
                    floatArrayOf(
                        (pixeles[i].toInt() and 0xFF).toFloat(),
                        (pixeles[i + 1].toInt() and 0xFF).toFloat(),
                        (pixeles[i + 2].toInt() and 0xFF).toFloat(),
                    )
                }
            }
        }
        val salida = Array(1) { FloatArray(modelo.clases.size) }
        modelo.interprete.run(entrada, salida)

        ResultadoClasificacion.de(
            clases = modelo.clases,
            probabilidades = salida[0].map { it.toDouble() },
            modeloVersion = modelo.version,
            umbralConfianza = umbralPersonalizado ?: modelo.umbralConfianza,
            milisegundos = System.currentTimeMillis() - inicio,
        )
    }

    /**
     * Detecta y clasifica cada grano del tablero con el modelo M2.
     *
     * La traducción de la salida de YOLO y la supresión de solapamientos están
     * en `:nucleo`, con sus tests: aquí solo se prepara la entrada y se llama
     * al intérprete.
     */
    suspend fun detectarGranos(
        tarea: String = "corte",
        pixeles: ByteArray,
        umbralConfianza: Double = 0.35,
        umbralSolape: Double = 0.45,
    ): ResultadoDeteccion = withContext(Dispatchers.Default) {
        val modelo = cargados[tarea] ?: throw (
            fallidos[tarea] ?: ModeloNoDisponible(
                TAREAS[tarea] ?: tarea,
                MotivoSinModelo.NO_INSTALADO,
            )
            )

        val lado = modelo.ladoEntrada
        val inicio = System.currentTimeMillis()

        // YOLO espera valores 0-1, no 0-255 como los clasificadores.
        val entrada = Array(1) {
            Array(lado) { y ->
                Array(lado) { x ->
                    val i = (y * lado + x) * 3
                    floatArrayOf(
                        (pixeles[i].toInt() and 0xFF) / 255f,
                        (pixeles[i + 1].toInt() and 0xFF) / 255f,
                        (pixeles[i + 2].toInt() and 0xFF) / 255f,
                    )
                }
            }
        }

        val forma = modelo.interprete.getOutputTensor(0).shape()
        if (forma.size != 3) {
            return@withContext ResultadoDeteccion(emptyList(), modelo.version)
        }
        val atributos = forma[1]
        val cajas = forma[2]
        val salida = Array(1) { Array(atributos) { FloatArray(cajas) } }
        modelo.interprete.run(entrada, salida)

        val matriz = Array(atributos) { fila ->
            DoubleArray(cajas) { c -> salida[0][fila][c].toDouble() }
        }
        val crudas: List<GranoDetectado> =
            leerCajasYolo(matriz, modelo.clases, umbralConfianza)

        ResultadoDeteccion(
            granos = suprimirSolapes(crudas, umbralSolape),
            modeloVersion = modelo.version,
            milisegundos = System.currentTimeMillis() - inicio,
        )
    }

    fun cerrar() {
        cargados.values.forEach { it.interprete.close() }
        cargados.clear()
    }

    companion object {
        /** Las tareas que la app puede usar, con el nombre que ve el usuario. */
        val TAREAS = mapOf(
            "mazorca" to "mazorcas",
            "corte" to "prueba de corte",
            "tostado" to "tostado",
            "chocolate" to "chocolate",
        )
    }
}

/** Un modelo instalado y listo para usar. */
class ModeloCargado(
    val tarea: String,
    val interprete: Interpreter,
    val clases: List<String>,
    val version: String,
    val umbralConfianza: Double,
    val ladoEntrada: Int,
)
