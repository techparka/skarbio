# ════════════════════════════════════════════════════════════════════
#  Skarbio — ProGuard rules
# ════════════════════════════════════════════════════════════════════

# Zachowaj info o źródle dla stack traces w crash reportach
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-renamesourcefileattribute SourceFile

# ── ML Kit ───────────────────────────────────────────────────────────
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.mlkit.**

# ── Room ─────────────────────────────────────────────────────────────
# Encje: Room generuje kod bazując na nazwach pól — nie wolno ich obcinać
-keep @androidx.room.Entity class * { *; }
-keepclassmembers @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keepclassmembers @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }
# Konkretne encje aplikacji
-keep class com.budgetapp.ocr.data.local.entity.** { *; }
-keep class com.budgetapp.ocr.data.local.AppDatabase { *; }

# ── Hilt / Dagger ────────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class dagger.** { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep class **_HiltComponents { *; }
-keep class **_HiltModules { *; }
-keep class **Hilt_** { *; }
-keep class *_GeneratedInjector { *; }
-dontwarn dagger.**
-dontwarn dagger.hilt.**

# ── Kotlin ────────────────────────────────────────────────────────────
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata { *; }
-dontwarn kotlin.**
-dontwarn kotlinx.**
# Kotlin Coroutines — pola volatile używane wewnętrznie
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ── Enums — używane przez Room TypeConverters i JSON ─────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── MPAndroidChart ────────────────────────────────────────────────────
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# ── PDFBox Android ────────────────────────────────────────────────────
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn org.apache.**

# ── WorkManager ───────────────────────────────────────────────────────
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.budgetapp.ocr.BillReminderWorker { *; }

# ── Navigation Component ──────────────────────────────────────────────
-keepnames class androidx.navigation.fragment.NavHostFragment

# ── Fragmenty i ViewModele aplikacji ─────────────────────────────────
# Navigation odtwarza fragmenty przez refleksję (nazwa klasy w nav_graph.xml)
-keep class com.budgetapp.ocr.ui.** extends androidx.fragment.app.Fragment { *; }
-keep class com.budgetapp.ocr.ui.** extends androidx.lifecycle.ViewModel { *; }
# Dialogi jako fragmenty
-keep class com.budgetapp.ocr.ui.** extends androidx.fragment.app.DialogFragment { *; }

# ── BackupManager — javax.crypto ──────────────────────────────────────
# javax.crypto jest wbudowane w Android, ale na wszelki wypadek
-keep class javax.crypto.** { *; }
-dontwarn javax.crypto.**

# ── Strip debug/verbose/info logs in release ─────────────────────────
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# ── Pozostałe ostrzeżenia do wyciszenia ───────────────────────────────
-dontwarn sun.misc.**
-dontwarn java.lang.invoke.**
-dontwarn okhttp3.**
