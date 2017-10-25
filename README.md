## Localytics Kit Integration

This repository contains the [Localytics](https://www.localytics.com) integration for the [mParticle Android SDK](https://github.com/mParticle/mparticle-android-sdk).

### Adding the integration

1. The Localytics Kit requires that you add Localytics' Maven server to your buildscript:

    ```
    repositories {
        maven { url 'http://maven.localytics.com/public' }
        ...
    }
    ```

2. Add the kit dependency to your app's build.gradle:

    ```groovy
    dependencies {
        compile 'com.mparticle:android-localytics-kit:5+'
    }
    ```
2. Follow the mParticle Android SDK [quick-start](https://github.com/mParticle/mparticle-android-sdk), then rebuild and launch your app, and verify that you see `"Localytics detected"` in the output of `adb logcat`.
3. Reference mParticle's integration docs below to enable the integration.

### Documentation

[Localytics integration](http://docs.mparticle.com/?java#localytics)

### License

[Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0)
