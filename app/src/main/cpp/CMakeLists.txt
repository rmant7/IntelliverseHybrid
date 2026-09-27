cmake_minimum_required(VERSION 3.22)
project(llama_jni)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# A debug APK compiles native code with CMAKE_BUILD_TYPE=Debug, and for Clang
# that means -O0: no inlining, no auto-vectorisation. For ggml, which is
# almost entirely tight numeric kernels, -O0 against -O3 is a tenfold
# difference. This project ships a debug-signed release build as its only
# build (see app/build.gradle.kts's own comment on signingConfig), so
# optimisation is forced back up for every configuration -- ported verbatim
# from rmant7/AI's own CMakeLists.txt, which hit this exact problem first.
foreach(cfg DEBUG RELWITHDEBINFO MINSIZEREL RELEASE)
    set(CMAKE_C_FLAGS_${cfg}   "-O3 -DNDEBUG" CACHE STRING "" FORCE)
    set(CMAKE_CXX_FLAGS_${cfg} "-O3 -DNDEBUG" CACHE STRING "" FORCE)
endforeach()
add_compile_options(-O3)

include(FetchContent)
FetchContent_Declare(
    llamacpp
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    GIT_TAG        b10448
    GIT_SHALLOW    TRUE
)

# Cross-compiling to Android: never let ggml probe the *host* CPU, and skip
# the parts of llama.cpp an embedded JNI build does not need.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)

# Baseline only (armv8-a, no dotprod/i8mm) -- deliberately, for this first
# working version: rmant7/AI's own project builds three CPU-feature variants
# and picks the best one at runtime (see its ai.localstudio.whisper.CpuVariant)
# specifically because a single dotprod or i8mm build SIGILLs on a CPU that
# doesn't support those instructions. Baseline is the one variant guaranteed
# to run on every arm64 device, at some inference-speed cost -- an acceptable
# tradeoff to avoid porting that whole runtime-detection/multi-variant Gradle
# module system in the same change as getting local inference working at all.
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_CPU_ARM_ARCH "armv8-a" CACHE STRING "" FORCE)
endif()

FetchContent_MakeAvailable(llamacpp)

# mtmd (multimodal: image/audio input) lives under tools/, which this project
# never adds as a whole -- LLAMA_BUILD_TOOLS is OFF above, deliberately, to
# skip llama-cli/llama-server/the rest of the CLI tools this app has no use
# for. The mtmd *library* target itself has no such gate in its own
# CMakeLists.txt (only its CLI executables do, behind that same
# LLAMA_BUILD_TOOLS), so it is added directly here instead of pulling in
# everything else under tools/ just to reach it. llama_jni.cpp (ported
# verbatim from rmant7/AI, only its JNI symbol names renamed for this app's
# package) still references mtmd's headers even though nothing in this app
# calls its vision entry points yet -- keeping the C++ file untouched rather
# than surgically removing that code is the lower-risk choice here.
add_subdirectory(${llamacpp_SOURCE_DIR}/tools/mtmd ${CMAKE_CURRENT_BINARY_DIR}/mtmd-build)

add_library(llama_jni SHARED llama_jni.cpp)

target_include_directories(llama_jni PRIVATE
    ${llamacpp_SOURCE_DIR}/include
    ${llamacpp_SOURCE_DIR}/ggml/include
    ${llamacpp_SOURCE_DIR}/tools/mtmd
)

find_library(log-lib log)
target_link_libraries(llama_jni llama ggml mtmd ${log-lib})
