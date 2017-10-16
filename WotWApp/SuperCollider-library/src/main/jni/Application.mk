# use gnustl_shared STL library
#APP_STL := gnustl_shared
# nope, needed for some std library features:
APP_STL := c++_static
# Build for all supported instruction sets except armeabi as it's obsolete and
# has problems with atomic operations: this is now actually set in build.gradle
# as:
# abiFilters "arm64-v8a", "armeabi-v7a", "x86_64", "x86", "mips64", "mips"
APP_PLATFORM=android-23
