# Keep TPStreams SDK stack traces readable when the consuming application
# enables R8 minification. Optimization remains enabled, while SDK class,
# method, and field names are preserved for diagnostics such as Sentry.
-keep,allowoptimization class com.tpstreams.player.** { *; }

# Preserve source filenames and line numbers in stack traces.
-keepattributes SourceFile,LineNumberTable
