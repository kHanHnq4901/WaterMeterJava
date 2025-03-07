# Giữ lại class quan trọng
-keep class com.example.myapplication.ui.** { *; }
-keep class com.example.myapplication.models.** { *; }

# Giữ lại các lớp liên quan đến WebView nếu bạn dùng JavaScript Interface
# -keepclassmembers class fqcn.of.javascript.interface.for.webview {
#    public *;
# }

# Giữ lại các annotation quan trọng
-keepattributes *Annotation*

# Tối ưu hóa code nhưng tránh lỗi runtime
-dontwarn androidx.**
-dontwarn com.example.myapplication.**

# Bật tối ưu hóa
-optimizationpasses 5
-allowaccessmodification
-mergeinterfacesaggressively

# Loại bỏ mã không sử dụng
-dontshrink
-dontoptimize
-dontobfuscate
