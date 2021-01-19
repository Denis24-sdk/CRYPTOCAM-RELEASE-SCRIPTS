# Cryptocam: record encrypted video

Recording certain situations can be dangerous for the person using the camera as well as the people being recorded. To mitigate the risk of the recording device or storage medium getting into the hands of an attacker with an interest in the recordings, Cryptocam allows you to record video and audio that is encrypted on the fly and never persisted to disk in cleartext form.

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/com.tnibler.cryptocam/)

## How it works

Cryptocam uses ffmpeg/libav's MP4-CENC functionality to encrypt video and audio at the sample level using AES-128. An AES key is generated for each recording and encrypted through OpenPGP along with other metadata before being written to disk.
Cryptocam uses OpenKeychain to encrypt using OpenPGP for flexible key management and an audited OpenPGP implementation.

Using OpenPGP with only the public key(s) stored on the device means that recordings can not be decrypted on the phone by you or an attacker under any circumstances.

## How to use it

Setup:

 - Install OpenKeychain onto your device
 - Import one or more public keys into OpenKeychain
 - Follow the setup in Cryptocam to set the key(s) you wish to use for encryption
 - Create a folder and set it as the output location where recordings will be stored
 
Before using Cryptocam, please choose your desired camera settings (resolution and framerate) and test that they actually work.

Not all configurations are supported by all devices, so if you choose one that your device does not support, Cryptocam falls back to your devices preferred configurations. So unless you want to be surprised by 1200x1200 video at 15 fps, test before using it!

So far, these devices have been tested:

 - Samsung Galaxy S9: up to 1920x1080 at 60fps. No 4K 
 - Google Pixel 2XL: up to 1920x1080 at 60fps. No 4K
 - LG G6: up to 1920x1080 at 60fps. No 4K
 
Please share your results on your device as well!

## Decrypting videos

Decrypting has only been tested on Linux. It probably works on MacOS as well though.

Clone [the companion script](https://gitlab.com/tnibler/cryptocam-companion) and follow the instructions in README.md.

Transfer the directory where videos are saved to your PC and `cd` to the location where you cloned the companion script. Then run:

```
 ./decrypt.sh /path/to/my/cryptocam/videos/* --destination /desired/destination
```

## Building

Before building the app, `ffmpeg` needs to be downloaded and compiled. A complete build can be run with `./complete_build.sh`.
Subsequent builds can be run through Android Studio or Gradle.

## Contributing

Please test it on your available devices and share the results. Create an issue or send me an email at cryptocam@tnibler.de to explain what is and what is not working.

If you know your way around video and ffmpeg, please feel free to look at Cryptocam's implementation of video/audio muxing and improve it. It works, but video and audio are not in sync at times and I lack the expertise to fix it.

	
