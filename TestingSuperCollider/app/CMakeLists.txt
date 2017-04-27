# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build your native library.
# This ensures that a certain set of CMake features is available to
# your build.

#Since this CMake uses file Globs always rerun the Cmake to ensure proper builds

cmake_minimum_required(VERSION 3.4.1)

# Specifies a library name, specifies whether the library is STATIC or
# SHARED, and provides relative paths to the source code. You can
# define multiple libraries by adding multiple add.library() commands,
# and CMake builds them for you. When you build your app, Gradle
# automatically packages shared libraries with your APK.

#To enable ISO C++ 2011 support
set (CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11")

add_library( # Specifies the name of the library.
             native-lib

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             src/main/cpp/native-lib.cpp )

#If you are not a fan of GLOB this might be problematic
#However, since I am using these cpp files as just libraries
#I think this is an acceptable use case
#adding files to this folder might fail your builds
file(GLOB SCSYNTH src/main/cpp/supercollider/server/scsynth/*.cpp src/main/cpp/supercollider/server/scsynth/*.h)

add_library(scsynth SHARED ${SCSYNTH})

# Specifies a path to native header files.
include_directories(src/main/cpp/supercollider/server/scsynth)
include_directories(src/main/cpp/supercollider/server/supernova)
include_directories(src/main/cpp/supercollider/include/plugin_interface)
include_directories(src/main/cpp/supercollider/include/common)
include_directories(src/main/cpp/supercollider/include/server)
include_directories(src/main/cpp/supercollider/include/lang)
include_directories(src/main/cpp/supercollider/common)
include_directories(src/main/cpp/supercollider/external_libraries/boost)
include_directories(src/main/cpp/supercollider/platform/mac)
include_directories(src/main/cpp/supercollider/platform/windows)
include_directories(src/main/cpp/supercollider/platform/linux)
include_directories(src/main/cpp/supercollider/platform/mac/SuperColliderAU/Source/)
include_directories(src/main/cpp/supercollider/external_libraries/boost_sync/include/)





