# FFmpegKit calls back into Java from native code.
-keep class com.antonkarpenko.ffmpegkit.** { *; }
-keepclassmembers class * { native <methods>; }
-keep,includedescriptorclasses class com.galandras12.handdroid.**$$serializer { *; }
-keepclassmembers class com.galandras12.handdroid.** { *** Companion; }
