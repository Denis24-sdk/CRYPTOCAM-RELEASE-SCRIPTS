FROM ubuntu:hirsute as build

RUN apt-get -y update
RUN apt-get -y install wget p7zip-full curl zip

RUN curl --proto '=https' --tlsv1.2 https://sh.rustup.rs -sSf | bash -s -- -y -t x86_64-pc-windows-gnu --profile minimal
ENV PATH="/root/.cargo/bin:${PATH}"

RUN DEBIAN_FRONTEND=noninteractive apt-get -y install g++-mingw-w64-x86-64-win32 \
                    gcc-mingw-w64-x86-64 \
                    mingw-w64-x86-64-dev \
                    build-essential \
                    qt5-qmake

ENV PATH="${PATH}:/usr/lib/qt5/bin"

RUN mkdir build
WORKDIR build

RUN wget https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-full-shared.7z
RUN 7z x ffmpeg-release-full-shared.7z
RUN mv ffmpeg-4.4-full_build-shared ffmpeg

RUN wget https://download.qt.io/online/qtsdkrepository/windows_x86/desktop/qt5_5152/qt.qt5.5152.win64_mingw81/5.15.2-0-202011130602qtbase-Windows-Windows_10-Mingw-Windows-Windows_10-X86_64.7z > /dev/null
RUN wget https://download.qt.io/online/qtsdkrepository/windows_x86/desktop/qt5_5152/qt.qt5.5152.win64_mingw81/5.15.2-0-202011130602qtdeclarative-Windows-Windows_10-Mingw-Windows-Windows_10-X86_64.7z > /dev/null
RUN wget https://download.qt.io/online/qtsdkrepository/windows_x86/desktop/qt5_5152/qt.qt5.5152.win64_mingw81/5.15.2-0-202011130602qtquickcontrols2-Windows-Windows_10-Mingw-Windows-Windows_10-X86_64.7z > /dev/null
RUN wget https://download.qt.io/online/qtsdkrepository/windows_x86/desktop/qt5_5152/qt.qt5.5152.win64_mingw81/5.15.2-0-202011130602qtsvg-Windows-Windows_10-Mingw-Windows-Windows_10-X86_64.7z > /dev/null
RUN wget https://download.qt.io/online/qtsdkrepository/windows_x86/desktop/qt5_5152/qt.qt5.5152.win64_mingw81/5.15.2-0-202011130602qtquickcontrols-Windows-Windows_10-Mingw-Windows-Windows_10-X86_64.7z > /dev/null
RUN wget https://download.qt.io/online/qtsdkrepository/windows_x86/desktop/tools_mingw/qt.tools.win64_mingw810/8.1.0-1-202004170606x86_64-8.1.0-release-posix-seh-rt_v6-rev0.7z > /dev/null

RUN 7z x -oqt_libs 5.15.2-0-202011130602qtbase-Windows-Windows_10-Mingw-Windows-Windows_10-X86_64.7z 5.15.2/mingw81_64
RUN 7z x -oqt_libs 5.15.2-0-202011130602qtdeclarative-Windows-Windows_10-Mingw-Windows-Windows_10-X86_64.7z 5.15.2/mingw81_64
RUN 7z x -oqt_libs 5.15.2-0-202011130602qtquickcontrols2-Windows-Windows_10-Mingw-Windows-Windows_10-X86_64.7z 5.15.2/mingw81_64
RUN 7z x -oqt_libs 5.15.2-0-202011130602qtsvg-Windows-Windows_10-Mingw-Windows-Windows_10-X86_64.7z 5.15.2/mingw81_64
RUN 7z x -oqt_libs 5.15.2-0-202011130602qtquickcontrols-Windows-Windows_10-Mingw-Windows-Windows_10-X86_64.7z
RUN 7z x -otools_mingw 8.1.0-1-202004170606x86_64-8.1.0-release-posix-seh-rt_v6-rev0.7z

RUN rm qt_libs/5.15.2/mingw81_64/bin/*.exe

COPY qml ./qml
COPY src ./src
COPY Cargo.toml cryptocam-companion.svg build.rs ./
RUN cargo update

RUN DEP_QT_INCLUDE_PATH="/build/qt_libs/5.15.2/mingw81_64/include" DEP_QT_LIBRARY_PATH="/build/qt_libs/5.15.2/mingw81_64/lib"  CXXFLAGS="-lstdc++,-lpthread -I/build/qt_libs/5.15.0/mingw81_64/include" CC=/usr/bin/x86_64-w64-mingw32-gcc CXX=/usr/bin/x86_64-w64-mingw32-g++ FFMPEG_INCLUDE_DIR=/build/ffmpeg/include FFMPEG_LIB_DIR=/build/ffmpeg/lib cargo build --target x86_64-pc-windows-gnu --release

RUN mkdir package
RUN rm ffmpeg/bin/avdevice*.dll ffmpeg/bin/postproc*.dll ffmpeg/bin/*.exe
RUN cp -r qt_libs/5.15.2/mingw81_64/* package/
RUN cp ffmpeg/bin/* package/bin
RUN cp /build/tools_mingw/Tools/mingw810_64/bin/libwinpthread-1.dll \
        /build/tools_mingw/Tools/mingw810_64/bin/libstdc++-6.dll \
        /build/tools_mingw/Tools/mingw810_64/bin/libgcc_s_seh-1.dll \
        package/bin

RUN rm -rf package/bin/libGLESv2.dll package/plugins/sqldrivers package/plugins/platforms/{qoffscreen.dll, qdirect2d.dll, qminimal.dll} package/plugins/qmltooling package/plugins/printsupport package/lib/{libqtfreetype.a, libQt5Bootstrap.a, libQt5FontDatabaseSupport.a, libQt5QmlDevTools.a, libQt5QmlDebug.a, libQt5FbSupport.a, libQt5DeviceDiscorverySupport.a, libQt5OpenGLExtensions.a} package/include package/doc

RUN cp target/x86_64-pc-windows-gnu/release/cryptocam-qt.exe package/bin/CryptocamCompanion.exe
RUN mv package CryptocamCompanion
RUN zip -r CryptocamCompanion.zip CryptocamCompanion

FROM scratch as output
COPY --from=build /build/CryptocamCompanion.zip /

