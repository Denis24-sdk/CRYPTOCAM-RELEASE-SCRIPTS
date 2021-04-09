FROM ubuntu:hirsute as build
RUN apt-get update --fix-missing
RUN DEBIAN_FRONTEND=noninteractive apt-get -y install python3 python3-setuptools python3-pip wget fakeroot libglib2.0-bin file desktop-file-utils libgdk-pixbuf2.0-dev librsvg2-dev libyaml-dev zsync git jq

RUN git clone https://github.com/AppImageCrafters/appimage-builder/ /opt/appimage-builder

WORKDIR /opt/appimage-builder
RUN git checkout v0.8.6
RUN python3 ./setup.py install && rm -rf /opt/appimage-builder

WORKDIR /tmp
RUN wget https://github.com/AppImage/AppImageKit/releases/download/continuous/appimagetool-x86_64.AppImage && \
    chmod +x /tmp/appimagetool-x86_64.AppImage && \
    cd /opt && /tmp/appimagetool-x86_64.AppImage --appimage-extract && \
    mv squashfs-root appimage-tool.AppDir && \
    ln -s /opt/appimage-tool.AppDir/AppRun /usr/bin/appimagetool && \
    rm /tmp/appimagetool-x86_64.AppImage

RUN wget https://github.com/NixOS/patchelf/releases/download/0.12/patchelf-0.12.tar.bz2 && \
    tar -xvf patchelf-0.12.tar.bz2  && \
    cd patchelf-0.12.20200827.8d3a16e &&  \
    ./configure && make && make install &&  \
    rm -rf patchelf-*


WORKDIR /
RUN apt-get -y autoclean
RUN apt-get -y install cargo rustc ffmpeg libavformat-dev libavcodec-dev libswscale-dev libavutil-dev qt5-qmake g++ qtbase5-dev qtdeclarative5-dev qtquickcontrols2-5-dev

RUN mkdir build
WORKDIR build

COPY qml ./qml
COPY src ./src
COPY Cargo.toml cryptocam-companion.svg build.rs ./
RUN cargo build --release -j8
RUN cp target/release/cryptocam-qt ./
COPY AppImageBuilder.yml ./

RUN appimage-builder --skip-tests

FROM scratch as appimage
COPY --from=build build/*.AppImage /
