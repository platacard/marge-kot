# Service

Kotlin microservice template produces self-executable jar application. For brevity, double-space
formatting is used. [`Ktor`](https://ktor.io/) is included to mock Digital Ocean healthy checks.

## Usage

1. Make sure you are signed in to your GitHub account, then just
   click [`here`](https://github.com/demidko/service/generate) to use template.
2. `App.kt` file is entry point.

## Build with Java

Execute `./gradlew clean build`. Your jar will be located at `./build/libs` with `-all.jar` postfix.
Now you can run:

```shell
java -jar service-all.jar
```

## Or, build with Docker

Execute `docker build . -t service`. Your image will be located at `docker images -a`. Now you can
run:

```shell
docker run -v `pwd`:`pwd` -w `pwd` -it --rm -p 80:80 service
```
