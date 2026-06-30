# Regole R8/ProGuard per la build release.
# Le librerie moderne (Retrofit 2.11, OkHttp 4, kotlinx-serialization) includono già
# le proprie regole "consumer"; queste sono esplicite per sicurezza e leggibilità.

# --- kotlinx.serialization ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
# Mantieni i serializer generati e le classi @Serializable dei modelli di rete
-keepclassmembers class it.casa.clima.net.** {
    *** Companion;
}
-keepclasseswithmembers class it.casa.clima.net.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class it.casa.clima.net.**$$serializer { *; }
-keep class it.casa.clima.net.** { *; }

# --- Retrofit ---
-keepattributes Signature, Exceptions
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
# Mantieni l'interfaccia API e i suoi metodi (lette via reflection da Retrofit)
-keep interface it.casa.clima.net.ClimaApi { *; }

# --- OkHttp ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
