# Reglas de ofuscación para la compilación de release.
#
# LiteRT/TensorFlow Lite usa reflexión para cargar sus delegados: sin estas
# reglas el APK compila, pero los modelos fallan al abrirse en el teléfono, que
# es el peor momento para enterarse.
-keep class org.tensorflow.** { *; }
-dontwarn org.tensorflow.**

# ML Kit para el escáner de QR
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# kotlinx.serialization genera serializadores por nombre de clase
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class ec.cacaotrace.** {
    *** Companion;
}
-keepclasseswithmembers class ec.cacaotrace.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room genera implementaciones en tiempo de compilación; no las toques.
-keep class androidx.room.** { *; }
-keep class ec.cacaotrace.datos.bd.** { *; }
