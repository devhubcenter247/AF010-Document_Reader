# Slim OpenCV 4.12.0

Local Java/JNI library for the scanner: core, imgproc and imgcodecs, with JPEG/PNG,
Carotene/NEON and Android OpenCVLoader/StaticHelper/Utils. Camera view wrappers
(including NativeCameraView, which needs the excluded videoio module) are omitted.
Generated Java sources are unmodified. libc++_shared.so comes from the app's other
native dependencies. Consumer rules preserve JNI class/field names under R8.

## Rebuild

Source tag: `4.12.0`. NDK `28.2.13676358`, CMake `3.31.6`, Ninja, Python 3.
Run from the Android project root. Adjust these paths on another machine:

```sh
SOURCE=/private/tmp/claude-501/-Volumes-Data-Azura-D010-Pdf-18/292320fe-63fd-4ba7-8ba9-18c3d2e2f497/scratchpad/opencv
BUILD=/private/tmp/claude-501/-Volumes-Data-Azura-D010-Pdf-18/292320fe-63fd-4ba7-8ba9-18c3d2e2f497/scratchpad/opencv-build
CMAKE=/Users/dongdong/Library/Android/sdk/cmake/3.31.6/bin/cmake
LLVM=/Users/dongdong/Library/Android/sdk/ndk/28.2.13676358/toolchains/llvm/prebuilt/darwin-x86_64/bin

for ABI in arm64-v8a armeabi-v7a; do
  "$CMAKE" -S "$SOURCE" -B "$BUILD/$ABI" -G Ninja \
    -DANDROID_ABI="$ABI" \
    -DOPENCV_DOWNLOAD_PATH="$BUILD/downloads" \
    -DCMAKE_TOOLCHAIN_FILE=/Users/dongdong/Library/Android/sdk/ndk/28.2.13676358/build/cmake/android.toolchain.cmake \
    -DCMAKE_MAKE_PROGRAM=/Users/dongdong/Library/Android/sdk/cmake/3.31.6/bin/ninja \
    -DANDROID_PLATFORM=android-26 \
    -DANDROID_STL=c++_shared \
    -DANDROID_SDK=/Users/dongdong/Library/Android/sdk \
    -DBUILD_LIST=core,imgproc,imgcodecs,java \
    -DCMAKE_BUILD_TYPE=Release \
    '-DCMAKE_C_FLAGS_RELEASE=-Os -DNDEBUG' \
    '-DCMAKE_CXX_FLAGS_RELEASE=-Os -DNDEBUG' \
    '-DCMAKE_SHARED_LINKER_FLAGS=-Wl,--gc-sections -Wl,-z,max-page-size=16384' \
    -DWITH_JPEG=ON \
    -DBUILD_JPEG=ON \
    -DWITH_PNG=ON \
    -DBUILD_PNG=ON \
    -DBUILD_ZLIB=ON \
    -DWITH_CAROTENE=ON \
    -DBUILD_SHARED_LIBS=OFF \
    -DBUILD_ANDROID_PROJECTS=OFF \
    -DBUILD_ANDROID_EXAMPLES=OFF \
    -DBUILD_TESTS=OFF \
    -DBUILD_PERF_TESTS=OFF \
    -DBUILD_EXAMPLES=OFF \
    -DBUILD_DOCS=OFF \
    -DWITH_TIFF=OFF \
    -DWITH_WEBP=OFF \
    -DWITH_OPENJPEG=OFF \
    -DWITH_JASPER=OFF \
    -DWITH_OPENEXR=OFF \
    -DWITH_IMGCODEC_HDR=OFF \
    -DWITH_IMGCODEC_SUNRASTER=OFF \
    -DWITH_IMGCODEC_PXM=OFF \
    -DWITH_IMGCODEC_PFM=OFF \
    -DWITH_IMGCODEC_GIF=OFF \
    -DWITH_AVIF=OFF \
    -DWITH_SPNG=OFF \
    -DWITH_OPENCL=OFF \
    -DWITH_TBB=OFF \
    -DWITH_IPP=OFF \
    -DWITH_ITT=OFF \
    -DWITH_EIGEN=OFF \
    -DWITH_PROTOBUF=OFF \
    -DWITH_FFMPEG=OFF \
    -DWITH_ANDROID_MEDIANDK=OFF \
    -DWITH_KLEIDICV=OFF \
    -DCV_TRACE=OFF \
    -DANDROID_PROJECTS_BUILD_TYPE=GRADLE \
    -DWITH_ADE=OFF
  "$CMAKE" --build "$BUILD/$ABI" \
    --target opencv_java opencv_java_android_source_copy -j 6
  mkdir -p "opencv/src/main/jniLibs/$ABI"
  cp "$BUILD/$ABI/jni/$ABI/libopencv_java4.so" "opencv/src/main/jniLibs/$ABI/"
  "$LLVM/llvm-strip" --strip-unneeded "opencv/src/main/jniLibs/$ABI/libopencv_java4.so"
  "$LLVM/llvm-readelf" -lW "opencv/src/main/jniLibs/$ABI/libopencv_java4.so"
done

GEN="$BUILD/arm64-v8a/modules/java_bindings_generator/gen"
mkdir -p opencv/src/main/java/org/opencv/android
cp -R "$GEN/java/." opencv/src/main/java/
for FILE in OpenCVLoader.java StaticHelper.java Utils.java; do
  cp "$GEN/android/java/org/opencv/android/$FILE" opencv/src/main/java/org/opencv/android/
done

"$CMAKE" --install "$BUILD/arm64-v8a" --component licenses --prefix "$BUILD/licenses"
mkdir -p opencv/src/main/assets/opencv-licenses
cp -R "$BUILD/licenses/sdk/etc/licenses/." opencv/src/main/assets/opencv-licenses/
cp "$SOURCE/LICENSE" opencv/src/main/assets/opencv-licenses/OpenCV-LICENSE
# Retain carotene-LICENSE, copied verbatim from hal/carotene/src/common.cpp's header.

./gradlew :app:assembleDevDebug
```

`ANDROID_PROJECTS_BUILD_TYPE=GRADLE` enables the Java bindings without Ant;
only the JNI/source-copy targets are built, so no upstream Gradle project is run.
`WITH_ADE=OFF` prevents the unused Graph API dependency download. The source tree
is untouched. The remaining BMP decoder is built into imgcodecs (no CMake toggle).
Only the two ARM ABIs are shipped. `x86_64` and `x86` build with the same flags
(add them to the loop, with `-DWITH_CAROTENE=OFF`: Carotene is the ARM NEON HAL)
and came out at 8.47 MB / 7.19 MB stripped.

## Verified native outputs

| ABI | Stripped bytes | Decimal MB | ELF LOAD alignment |
| --- | ---: | ---: | --- |
| arm64-v8a | 6,110,144 | 6.11 | 0x4000 (16 KB), all segments |
| armeabi-v7a | 4,406,476 | 4.41 | 0x4000 (16 KB), all segments |

Baseline sizes supplied for the Maven AAR: approximately 23 MB / 15.6 MB.
