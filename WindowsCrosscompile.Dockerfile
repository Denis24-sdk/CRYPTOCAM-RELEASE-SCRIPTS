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

RUN wget https://download.qt.io/online/qtsdkrepository/windows_x86/desktop/qt5_5150/qt.qt5.5150.win64_mingw81/5.15.0-0-202005150700qtbase-Windows-Windows_10-Mingw-Windows-Windows_10-X86_64.7z
RUN wget https://download.qt.io/online/qtsdkrepository/windows_x86/desktop/qt5_5150/qt.qt5.5150.win64_mingw81/5.15.0-0-202005150700qtdeclarative-Windows-Windows_10-Mingw-Windows-Windows_10-X86_64.7z

RUN wget https://download.qt.io/online/qtsdkrepository/windows_x86/desktop/qt5_5150/qt.qt5.5150.win64_mingw81/5.15.0-0-202005150700qtquickcontrols2-Windows-Windows_10-Mingw-Windows-Windows_10-X86_64.7z
RUN wget https://download.qt.io/online/qtsdkrepository/windows_x86/desktop/qt5_5150/qt.qt5.5150.win64_mingw81/5.15.0-0-202005150700qtsvg-Windows-Windows_10-Mingw-Windows-Windows_10-X86_64.7z
RUN wget https://download.qt.io/online/qtsdkrepository/windows_x86/desktop/qt5_5150/qt.qt5.5150.win64_mingw81/5.15.0-0-202005150700qtquickcontrols-Windows-Windows_10-Mingw-Windows-Windows_10-X86_64.7z

RUN 7z x -oqt_libs 5.15.0-0-202005150700qtbase-Windows-Windows_10-Mingw-Windows-Windows_10-X86_64.7z 5.15.0/mingw81_64
RUN 7z x -oqt_libs 5.15.0-0-202005150700qtdeclarative-Windows-Windows_10-Mingw-Windows-Windows_10-X86_64.7z 5.15.0/mingw81_64
RUN 7z x -oqt_libs 5.15.0-0-202005150700qtquickcontrols2-Windows-Windows_10-Mingw-Windows-Windows_10-X86_64.7z 5.15.0/mingw81_64
RUN 7z x -oqt_libs 5.15.0-0-202005150700qtsvg-Windows-Windows_10-Mingw-Windows-Windows_10-X86_64.7z 5.15.0/mingw81_64
RUN 7z x -oqt_libs 5.15.0-0-202005150700qtquickcontrols-Windows-Windows_10-Mingw-Windows-Windows_10-X86_64.7z

RUN cp -r qt_libs/5.15.0/mingw81_64/bin/*.dll /usr/x86_64-w64-mingw32/lib/

COPY qml ./qml
COPY src ./src
COPY Cargo.toml cryptocam-companion.svg build.rs ./
RUN cargo update

RUN CFLAGS="-static-libgcc -Wl,-Bstatic,-lpthread,-Wl,-Bdynamic" CXXFLAGS="-static-libgcc -static-libstdc++ -Wl,-Bstatic,-lstdc++,-lpthread,-Wl,-Bdynamic -I/build/qt_libs/5.15.0/mingw81_64/include" CC=/usr/bin/x86_64-w64-mingw32-gcc CXX=/usr/bin/x86_64-w64-mingw32-g++ FFMPEG_INCLUDE_DIR=/build/ffmpeg/include FFMPEG_LIB_DIR=/build/ffmpeg/bin cargo build --target x86_64-pc-windows-gnu --release --verbose

RUN mkdir package
RUN rm ffmpeg/bin/avdevice*.dll ffmpeg/bin/postproc*.dll ffmpeg/bin/*.exe
RUN cp -r qt_libs/5.15.0/mingw81_64/* package/
RUN cp ffmpeg/bin/* package/bin
RUN cp /usr/x86_64-w64-mingw32/lib/libwinpthread-1.dll \
        /usr/lib/gcc/x86_64-w64-mingw32/10-win32/libstdc++-6.dll \
        /usr/lib/gcc/x86_64-w64-mingw32/10-win32/libgcc_s_seh-1.dll \
        package/bin

RUN cp target/x86_64-pc-windows-gnu/release/cryptocam-qt.exe package/bin/CryptocamCompanion.exe
RUN mv package CryptocamCompanion
RUN zip -r CryptocamCompanion.zip CryptocamCompanion

FROM scratch as output
COPY --from=build /build/CryptocamCompanion.zip /

