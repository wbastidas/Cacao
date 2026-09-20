# Reglas de ofuscacion para la compilacion de release.
#
# TensorFlow Lite usa reflexion para cargar sus delegados: sin estas reglas el
# APK compila, pero los modelos fallan al abrirse en el telefono, que es el
# peor momento para enterarse.
-keep class org.tensorflow.** { *; }
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.**

# Drift/sqlite3
-keep class com.tekartik.sqflite.** { *; }
-dontwarn com.tekartik.sqflite.**

# Plugins de camara y escaner de QR
-keep class io.flutter.plugins.imagepicker.** { *; }
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
