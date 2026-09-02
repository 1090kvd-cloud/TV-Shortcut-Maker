# Keep the launch proxy activity — it is only ever started through pinned shortcuts,
# so R8 could otherwise think it is unused.
-keep class com.tvshortcut.maker.launch.LaunchProxyActivity { *; }

# Compose keeps its own rules via the AAR consumer files; nothing else is needed.
