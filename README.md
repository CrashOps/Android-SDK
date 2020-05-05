# CrashOps Android SDK
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

This library will help you monitor your Android app's crashes.

## Installation
### 🔌 & ▶️

### Install via gradle

#### Using the plain and common maven
[![](https://img.shields.io/badge/jcenter-v0.1.0-green)](https://repo.dotcms.com/artifactory/simple/jcenter/com/crashops/sdk/crashops/)

In your app-level "build.gradle" file, put:
```
   dependencies {
        implementation 'com.crashops.sdk:crashops:0.1.0'
   }
```


#### Using "jitpack.io"
[![](https://jitpack.io/v/CrashOps/Android-SDK.svg)](https://jitpack.io/#CrashOps/Android-SDK)

In your root-level "build.gradle" file, put:
```
    allprojects {
        repositories {
            jcenter()
            maven { url "https://jitpack.io" }
        }
   }
```

In your app-level "build.gradle" file, put:
```
   dependencies {
        implementation 'com.github.CrashOps:Android-SDK:0.1.12'
   }
```

## Usage

### Set Application Key

To recognize your app in CrashOps servers you need an application key, you can set it via code (programmatically) either via config file.

#### Set an application key via code
```Kotlin
// Kotlin
CrashOps.getInstance().setAppKey("app's-key-received-from-CrashOps-support")
```

```Java
// Java (pretty much like Kotlin 🙂)
CrashOps.getInstance().setAppKey("app's-key-received-from-CrashOps-support");
```

#### Set an application key via config file

Use the [crashops_config.xml file](https://github.com/CrashOps/Android-SDK/blob/0.1.0/library/src/main/res/values/crashops_config.xml#L10) and place it in the [values](https://github.com/CrashOps/Flutter-Example/tree/aa93335d85cb3d70c10ba1dd0222f8ba3cf225ab/android/app/src/main/res/values) folder.


### How do I turn CrashOps off / on?
By default, CrashOps is enabled and it runs automatically as your app runs  (plug n' play) but you always can control and enable / disable its behavior with two approaches: dynamically or statically.

**Dynamically:** Programmatically call the method `disable()` / `enable()` as demonstrated here:
```kotlin
// Kotlin
CrashOps.getInstance().disable()
// OR:
CrashOps.getInstance().enable()
```

```java
// Java (pretty much like Kotlin 🙂)
CrashOps.getInstance().disable();
// OR:
CrashOps.getInstance().enable();
```

**Statically:** Add a [crashops_config.xml file](https://github.com/CrashOps/Android-SDK/blob/0.1.0/library/src/main/res/values/crashops_config.xml) to your 'values' folder and the SDK will read it in every app launch (using this approach may still be overridden by the dynamic approach). Do you wish to set different values for _debug_ / _release_ ? use [Android flavors](https://developer.android.com/studio/build/build-variants).


## Acknowledgments

We're using [retrofit](https://square.github.io/retrofit/) to upload our log files.
