# kotlinx.serialization and Retrofit ship their own consumer rules.
-dontwarn org.jspecify.annotations.**
-dontwarn com.google.re2j.**

# wordpress-rs calls into Rust through UniFFI-generated JNA bindings. JNA reads each Structure's
# @FieldOrder and the bindings' native method names by reflection, so R8 mustn't strip or rename
# them (rules as in WordPress-Android).
-keep class com.sun.jna.** { *; }
-keep class * extends com.sun.jna.Structure { *; }
-keepclassmembers class * extends com.sun.jna.Structure { *; }
-keep,allowobfuscation @interface com.sun.jna.Structure$FieldOrder
-keep @com.sun.jna.Structure$FieldOrder class * { *; }
-keep class uniffi.** { *; }
-dontwarn java.awt.**
