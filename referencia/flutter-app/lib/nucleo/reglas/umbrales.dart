/// Umbrales configurables de la tabla 5.1 de la ERS.
///
/// NINGUNO de estos valores está cableado en la lógica: el usuario los edita
/// desde Ajustes (RF-CFG-03) y pueden llegar por Remote Config sin publicar una
/// versión nueva de la app (RNF-12). Lo que aquí se define son los valores POR
/// DEFECTO y su explicación en español.
library;

/// Un umbral editable, con lo que el usuario necesita para decidir si cambiarlo.
class Umbral {
  const Umbral({
    required this.clave,
    required this.etiqueta,
    required this.porDefecto,
    required this.unidad,
    required this.explicacion,
    this.minimo,
    this.maximo,
  });

  final String clave;

  /// Lo que se muestra en Ajustes.
  final String etiqueta;
  final double porDefecto;
  final String unidad;

  /// Por qué existe este umbral, en palabras simples.
  final String explicacion;

  /// Rango de valores razonables, para evitar errores de dedo.
  final double? minimo;
  final double? maximo;

  bool esValido(double v) =>
      (minimo == null || v >= minimo!) && (maximo == null || v <= maximo!);
}

/// Catálogo de todos los umbrales de la app.
class CatalogoUmbrales {
  static const List<Umbral> todos = [
    // ----- Reposo -----
    Umbral(
      clave: 'reposo_dias_min',
      etiqueta: 'Días mínimos de reposo',
      porDefecto: 3,
      unidad: 'días',
      explicacion: 'El reposo de la mazorca cerrada ayuda a que la fermentación '
          'arranque mejor. Menos de 3 días suele ser poco.',
      minimo: 0,
      maximo: 15,
    ),
    Umbral(
      clave: 'reposo_dias_max',
      etiqueta: 'Días máximos de reposo',
      porDefecto: 6,
      unidad: 'días',
      explicacion: 'Pasados estos días la mazorca empieza a germinar o a '
          'pudrirse. La app avisa el día de apertura.',
      minimo: 1,
      maximo: 20,
    ),
    Umbral(
      clave: 'reposo_dias_alerta',
      etiqueta: 'Días de reposo que disparan alerta',
      porDefecto: 7,
      unidad: 'días',
      explicacion: 'Si el lote lleva más de estos días sin abrirse, la app '
          'avisa de que se está pasando.',
      minimo: 1,
      maximo: 30,
    ),

    // ----- Apertura -----
    Umbral(
      clave: 'baba_masa_minima_kg',
      etiqueta: 'Masa mínima de baba',
      porDefecto: 20,
      unidad: 'kg',
      explicacion: 'Por debajo de esta masa el montón no conserva el calor y la '
          'fermentación se enfría. La app recomienda añadir cáscara.',
      minimo: 1,
      maximo: 500,
    ),
    Umbral(
      clave: 'baba_por_mazorca_kg',
      etiqueta: 'Baba esperada por mazorca',
      porDefecto: 0.17,
      unidad: 'kg',
      explicacion: 'Rendimiento típico del CCN-51. Sirve para avisar si el peso '
          'registrado no cuadra con el número de mazorcas.',
      minimo: 0.01,
      maximo: 2,
    ),

    // ----- Fermentación -----
    Umbral(
      clave: 'volteo_horas',
      etiqueta: 'Horas entre volteos',
      porDefecto: 24,
      unidad: 'h',
      explicacion: 'Cada cuánto hay que voltear la masa para que fermente parejo.',
      minimo: 6,
      maximo: 72,
    ),
    Umbral(
      clave: 'volteo_horas_alerta',
      etiqueta: 'Horas sin voltear que disparan alerta',
      porDefecto: 26,
      unidad: 'h',
      explicacion: 'Un poco más que el intervalo normal, para no molestar por '
          'unos minutos de retraso.',
      minimo: 6,
      maximo: 96,
    ),
    Umbral(
      clave: 'ferm_temp_minima_c',
      etiqueta: 'Temperatura mínima de fermentación',
      porDefecto: 40,
      unidad: '°C',
      explicacion: 'Pasadas las primeras horas la masa debe estar caliente. Si '
          'no llega a esta temperatura, la fermentación está fría.',
      minimo: 20,
      maximo: 60,
    ),
    Umbral(
      clave: 'ferm_temp_minima_desde_h',
      etiqueta: 'Desde qué hora se exige esa temperatura',
      porDefecto: 60,
      unidad: 'h',
      explicacion: 'Antes de estas horas es normal que aún esté fría.',
      minimo: 0,
      maximo: 200,
    ),
    Umbral(
      clave: 'ferm_temp_maxima_c',
      etiqueta: 'Temperatura máxima de fermentación',
      porDefecto: 52,
      unidad: '°C',
      explicacion: 'Por encima de esto el grano se cocina y aparecen sabores '
          'raros. Hay que voltear y destapar.',
      minimo: 35,
      maximo: 70,
    ),
    Umbral(
      clave: 'ferm_duracion_maxima_dias',
      etiqueta: 'Duración máxima de la fermentación',
      porDefecto: 7,
      unidad: 'días',
      explicacion: 'Más allá de esto el lote se sobrefermenta y toma olor a '
          'amoniaco.',
      minimo: 2,
      maximo: 15,
    ),

    // ----- Secado -----
    Umbral(
      clave: 'humedad_maxima_pct',
      etiqueta: 'Humedad máxima del grano al cerrar el secado',
      porDefecto: 7,
      unidad: '%',
      explicacion: 'Con más humedad el grano cría moho en el almacén. La app no '
          'deja pasar a almacenamiento sin que lo confirmes.',
      minimo: 3,
      maximo: 15,
    ),

    // ----- Almacenamiento -----
    Umbral(
      clave: 'almacen_hr_maxima_pct',
      etiqueta: 'Humedad relativa máxima en el almacén',
      porDefecto: 70,
      unidad: '%',
      explicacion: 'Por encima de esto el grano vuelve a tomar humedad y hay '
          'riesgo de moho.',
      minimo: 30,
      maximo: 95,
    ),
    Umbral(
      clave: 'almacen_dias_inspeccion',
      etiqueta: 'Días entre inspecciones del almacén',
      porDefecto: 7,
      unidad: 'días',
      explicacion: 'Cada cuánto revisar sacos, olor, plagas y moho.',
      minimo: 1,
      maximo: 60,
    ),

    // ----- Tostado -----
    Umbral(
      clave: 'tostado_merma_min_pct',
      etiqueta: 'Merma mínima esperada en el tostado',
      porDefecto: 3,
      unidad: '%',
      explicacion: 'Si se pierde menos peso del esperado, revisa la balanza.',
      minimo: 0,
      maximo: 30,
    ),
    Umbral(
      clave: 'tostado_merma_max_pct',
      etiqueta: 'Merma máxima esperada en el tostado',
      porDefecto: 12,
      unidad: '%',
      explicacion: 'Si se pierde más peso del esperado, el tostado fue excesivo.',
      minimo: 1,
      maximo: 40,
    ),
    Umbral(
      clave: 'cascarilla_esperada_pct',
      etiqueta: 'Cascarilla esperada al descascarillar',
      porDefecto: 15,
      unidad: '%',
      explicacion: 'Proporción normal de cascarilla sobre el grano tostado.',
      minimo: 5,
      maximo: 35,
    ),
    Umbral(
      clave: 'cascarilla_desvio_pct',
      etiqueta: 'Desvío de cascarilla que dispara alerta',
      porDefecto: 5,
      unidad: 'puntos',
      explicacion: 'Mucha cascarilla puede significar que se van nibs con ella; '
          'muy poca, que quedan nibs sucios.',
      minimo: 1,
      maximo: 20,
    ),

    // ----- Atemperado -----
    Umbral(
      clave: 'cuarto_temp_maxima_c',
      etiqueta: 'Temperatura máxima del cuarto de atemperado',
      porDefecto: 22,
      unidad: '°C',
      explicacion: 'Con el cuarto caliente el chocolate no cristaliza bien y '
          'aparece fat bloom (velo grisáceo).',
      minimo: 10,
      maximo: 35,
    ),
    Umbral(
      clave: 'cuarto_hr_maxima_pct',
      etiqueta: 'Humedad máxima del cuarto de atemperado',
      porDefecto: 60,
      unidad: '%',
      explicacion: 'Con humedad alta el azúcar se disuelve en la superficie y '
          'aparece sugar bloom.',
      minimo: 20,
      maximo: 90,
    ),
    Umbral(
      clave: 'atemperado_temp_trabajo_min_c',
      etiqueta: 'Temperatura de trabajo mínima',
      porDefecto: 31,
      unidad: '°C',
      explicacion: 'Rango en el que se moldea el chocolate negro bien atemperado.',
      minimo: 25,
      maximo: 35,
    ),
    Umbral(
      clave: 'atemperado_temp_trabajo_max_c',
      etiqueta: 'Temperatura de trabajo máxima',
      porDefecto: 32,
      unidad: '°C',
      explicacion: 'Por encima de esto se deshacen los cristales buenos.',
      minimo: 26,
      maximo: 38,
    ),

    // ----- Laboratorio -----
    Umbral(
      clave: 'cadmio_limite_mg_kg',
      etiqueta: 'Límite de cadmio',
      porDefecto: 0.80,
      unidad: 'mg/kg',
      explicacion: 'Límite de referencia del Reglamento (UE) 2023/915 para '
          'chocolate con ≥ 50 % de cacao. Confírmalo para tu mercado de destino.',
      minimo: 0.01,
      maximo: 10,
    ),

    // ----- IA -----
    Umbral(
      clave: 'ia_confianza_minima',
      etiqueta: 'Confianza mínima de la IA',
      porDefecto: 0.60,
      unidad: '',
      explicacion: 'Si el modelo no llega a esta seguridad, la app dice "No '
          'estoy seguro" y te pide a ti la decisión.',
      minimo: 0.1,
      maximo: 0.99,
    ),

    // ----- Rendimientos -----
    Umbral(
      clave: 'rendimiento_desvio_pct',
      etiqueta: 'Desvío de rendimiento que dispara aviso',
      porDefecto: 20,
      unidad: '%',
      explicacion: 'Si una etapa rinde muy distinto de lo esperado, casi siempre '
          'es un error al anotar el peso.',
      minimo: 5,
      maximo: 80,
    ),
  ];

  static final Map<String, Umbral> porClave = {
    for (final u in todos) u.clave: u,
  };

  /// Valores por defecto, listos para guardar en la base la primera vez.
  static Map<String, double> valoresPorDefecto() => {
        for (final u in todos) u.clave: u.porDefecto,
      };
}

/// Los umbrales vigentes: los de fábrica, con encima los que el usuario cambió.
class Umbrales {
  Umbrales(Map<String, double> valores)
      : _valores = {...CatalogoUmbrales.valoresPorDefecto(), ...valores};

  Umbrales.porDefecto() : _valores = CatalogoUmbrales.valoresPorDefecto();

  final Map<String, double> _valores;

  double operator [](String clave) {
    final v = _valores[clave];
    if (v == null) {
      throw ArgumentError('No existe el umbral "$clave"');
    }
    return v;
  }

  int entero(String clave) => this[clave].round();

  Map<String, double> get valores => Map.unmodifiable(_valores);

  /// Devuelve una copia con un umbral cambiado, validando el rango.
  Umbrales con(String clave, double valor) {
    final def = CatalogoUmbrales.porClave[clave];
    if (def == null) throw ArgumentError('No existe el umbral "$clave"');
    if (!def.esValido(valor)) {
      throw ArgumentError(
        '${def.etiqueta}: $valor ${def.unidad} está fuera del rango '
        '${def.minimo} – ${def.maximo}',
      );
    }
    return Umbrales({..._valores, clave: valor});
  }
}
