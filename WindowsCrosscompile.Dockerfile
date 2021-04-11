FROM ubuntu:hirsute as build

RUN apt-get -y update
RUN apt-get -y install wget p7zip-full curl zip

RUN curl --proto '=https' --tlsv1.2 https://sh.rustup.rs -sSf | bash -s -- -y -t x86_64-pc-windows-gnu --profile minimal
ENV PATH="/root/.cargo/bin:${PATH}"

RUN mkdir build
WORKDIR build
COPY qml ./qml
COPY src ./src
COPY Cargo.toml cryptocam-companion.svg build.rs ./

ENV PATH="${PATH}:/root/.cargo/bin"

RUN DEBIAN_FRONTEND=noninteractive apt-get -y install g++-mingw-w64-x86-64-win32 \
                    build-essential \
                    qt5-qmake \
                    qtbase5-dev \
                    qtdeclarative5-dev \
                    qtquickcontrols2-5-dev

ENV PATH="${PATH}:/usr/lib/qt5/bin"

RUN wget https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-full-shared.7z
RUN 7z x ffmpeg-release-full-shared.7z
RUN mv ffmpeg-4.4-full_build-shared ffmpeg

RUN cargo update

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

RUN CC=/usr/bin/x86_64-w64-mingw32-gcc CXX=/usr/bin/x86_64-w64-mingw32-g++ FFMPEG_INCLUDE_DIR=/build/ffmpeg/include FFMPEG_LIB_DIR=/build/ffmpeg/lib cargo build --target x86_64-pc-windows-gnu --release

RUN mkdir package
RUN rm ffmpeg/bin/avdevice*.dll ffmpeg/bin/postproc*.dll ffmpeg/bin/*.exe
RUN cp ffmpeg/bin/* package/

RUN cp -r qt_libs/5.15.0/mingw81_64/ package/qt5

RUN cp target/x86_64-pc-windows-gnu/release/cryptocam-qt.exe package/CryptocamCompanion.exe
RUN mv package CryptocamCompanion
RUN zip -r CryptocamCompanion.zip CryptocamCompanion

FROM scratch as output
COPY --from=build /build/CryptocamCompanion.zip /
COPY --from=build /build/CryptocamCompanion/CryptocamCompanion.exe /
