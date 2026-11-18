-keep class net.zetetic.** { *; }
-keep class org.bouncycastle.crypto.generators.Argon2BytesGenerator { *; }
-keep class org.bouncycastle.crypto.params.Argon2Parameters** { *; }
-keepclassmembers class com.cardvault.nfc.** {
    *** Companion;
}
-keepclasseswithmembers class com.cardvault.nfc.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn org.bouncycastle.**
