# MND Player — Native Android Starter

A fresh Kotlin Android project for Maa Nirmala DJ Player. It is independent of the Maa Nirmala DJ websites.

## Included
- Queries Android MediaStore for device video and audio libraries.
- Requests runtime media permissions.
- All / Videos / Audio filters and title search.
- Inline Media3/ExoPlayer playback controls.
- Shows duration and file size.
- Basic Android "Open with" / share intent support for audio and video.
- Dark premium interface with gold accents.

## Build on an Android phone
1. Install an Android IDE that can open/build Gradle Android projects, or use a cloud Android build environment.
2. Extract `MND_Player_Android.zip`.
3. Open the extracted `MNDPlayer` folder as an Android/Gradle project.
4. Allow Gradle sync and install required Android SDK Platform 35 and a compatible JDK (17).
5. Build the debug APK, then install it on your device.
6. On first launch, grant video and audio access. If permission is denied, open Android Settings > Apps > MND Player > Permissions.

## Important limits
- This is source code, not a compiled or tested APK. A compatible Android SDK/Gradle build is needed to produce the APK.
- MediaStore detects indexed media in shared storage. Files hidden from MediaStore, private app folders, cloud-only files, or folders without access may not appear.
- Codec support depends on Android/device decoders. Some formats/codecs may need an additional decoder strategy.
- This starter is not yet a full MX Player replacement: no background playback service, subtitle file browser, equalizer UI, playback history persistence, playlist manager, picture-in-picture, or gesture overlays yet.
- Android 13+ uses separate audio/video permissions. Users can deny either permission independently; the starter asks for both.
- The source targets SDK 35 and uses Android Gradle Plugin 8.9.2 / Kotlin 2.1.20.

## Package
`com.maanirmala.mndplayer`
