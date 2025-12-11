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

This application can be bundled as a self-contained MacOS application
(an `.app` bundle) using this command:

```
$ mvn package -Pjpackage
```

This will create a directory target/KoiBots QR Scout Hub.app containing the
complete MacOS application. You can copy this file to other machines as long
as the hardware architecture matches that of the source machine (e.g. aarch64
to aarch64 or x86-64 to x86-64).

Building an application on Windows and Linux is a future goal.
