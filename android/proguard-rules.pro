# Scoped to HMS: a consumer file is merged into the app's R8 run, and AGP 9 rejects global
# options such as -ignorewarnings here (which also silenced every other SDK's warnings).
-dontwarn com.huawei.**
-keepattributes *Annotation*
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable
-keep class com.huawei.hianalytics.**{*;}
-keep class com.huawei.updatesdk.**{*;}
-keep class com.huawei.hms.**{*;}
