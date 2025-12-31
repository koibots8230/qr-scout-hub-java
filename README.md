# qr-scout-hub-java
A data aggregation hub for QR Scout.

# How to build and run
This project uses Apache Maven to build. You will need a JDK Java 21 or later)
and Maven 3.6 or later installed to build.

```
$ mvn package
```

This builds a JAR file containing the application. The first time you run
Maven on this project, it will likely download a **large** number of
dependencies to your computer. This is because the JavaCV library includes
a large number of supported platforms and architectures. We may be able to
trim these down over time to only those platforms we choose to support.

```
$ mvn dependency:copy-dependencies
```

This will copy all dependencies into target/dependency.

```
$ ./run.sh
```

This will launch the QR Scout Hub application.

# How to package

## MacOS

On MacOS, you can build a self-contained MacOS application bundle (an `.app`
directory) using this command:

```
$ mvn package -Pjpackage-mac
```

This will create a directory `target/KoiBots QR Scout Hub.app` containing the
complete MacOS application. You can copy this file to other machines as long
as the hardware architecture matches that of the source machine (e.g. aarch64
to aarch64 or x86-64 to x86-64).

## Microsoft Windows

On Microsoft Windows, you can build an application directory using this command:

```
C:> mvn package -Pjpackage-win
```

This will create a directory `target/KoiBots QR Scout Hub` containing a Windows
.exe binary and all supporting files. You can copy this to other machines as long
as the hardware architecture matches that of the source machine (e.g. x86-64 to
x86-64 or arm64 to arm64).

## Linux

On Linux, you can build an application directory using this command:

```
$ mvn package -Pjpackage-linux-app
```

This will create a directory `target/KoiBots QR Scout Hub` containing a binary
launcher and all supporting files. you can copy this to other machines as long
as the hardware architecture matches that of the source machine (e.g. x86-x64 to
x86-64 or arm64 to arm64).

# Testing individual components

There are command-line interfaces for the Project, GameConfig, and CodeScanner
classes.

## Project
The Project class can create a new project, and get info for, insert data
into, or export data from an existing project. After running `mvn package`
and `mvn dependency:copy-dependencies`,
you can run it like this:

```
$ MAIN_CLASS=com.koibots.scout.hub.Project ./run.sh
```

This will issue a short help text for what options are available.

## GameConfig
The GameConfig is able to read a JSON file and print basic information about
it.

```
$ MAIN_CLASS=com.koibots.scout.hub.GameConfig ./run.sh
```

## CodeScanner
CodeScanner will start a camera and scan a QR code

```
$ MAIN_CLASS=com.koibots.scout.hub.CodeScanner ./run.sh
```
