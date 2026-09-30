# Libraries ship their own consumer rules (Room, WorkManager, OkHttp, kotlinx.serialization,
# Play services, Glance). The app itself uses no reflection.

# Keep readable stack traces; the mapping file from each release build de-obfuscates names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
