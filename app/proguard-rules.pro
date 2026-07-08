# Homer ProGuard/R8 rules.
# Media3, Hilt, Room, and OkHttp ship consumer rules; keep this minimal and add
# only what real release builds reveal is stripped.

# Keep Compose runtime metadata that R8 occasionally over-strips.
-dontwarn org.jetbrains.annotations.**
