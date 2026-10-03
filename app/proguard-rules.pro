# FFmpegKit calls back into Java from native code.
-keep class com.antonkarpenko.ffmpegkit.** { *; }
-keepclassmembers class * { native <methods>; }
-keep,includedescriptorclasses class com.galandras12.unofficialhandbrake.**$$serializer { *; }
-keepclassmembers class com.galandras12.unofficialhandbrake.** { *** Companion; }
