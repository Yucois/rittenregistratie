# Alleen van kracht als minify in build.gradle.kts wordt aangezet.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class nl.exitinflex.rittenregistratie.kern.** {
    *** Companion;
}
