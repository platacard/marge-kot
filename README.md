# Microservice

Template produces self-executable jar with API at localhost.

## Usage

1. Make sure you are signed in to your GitHub account, then just
   click [`here`](https://github.com/demidko/application/generate) to use template.
2. [`App.kt`](src/main/kotlin/app/example/App.kt) file is entry point.

## Build with Java

Execute `./gradlew clean build`. Your jar will be located at `./build/libs` with `-boot.jar` postfix.
Now you can run:

```shell
java -jar --enable-preview build/libs/application-boot.jar
```

## Or, build with Docker

Execute `docker build . -t app`. Your image will be located at `docker images -a`. Now you can
run:

```shell
docker run -v `pwd`:`pwd` -w `pwd` -it --rm -p 80:8080 app
```

Rest API will available at your [localhost](http://localhost/).

## FYI

### Spring Boot Params

If you specify, for example, the annotation

```kotlin
@Value("\${myCalendars.url:http://default-calendar.com}")
lateinit var myCalendarUrl
```

then the value will be taken from the environment variable `MYCALENDAR_URL`. In addition, such a variable can also be
set in the usual way, from the application.yml file or from command line parameters.

### Kotlin value classes

You can
use [multi-field value classes](https://github.com/zhelenskiy/KEEP/blob/patch-6/proposals/multi-field-value-classes.md)
for your entities.

### Code completion for es6 imports in IntelliJ IDEA

You can download TypeScript definitions for es6 imports
using [an intention action](https://www.jetbrains.com/help/webstorm/configuring-javascript-libraries.html#ws_js_configuring_libraries_ts_definition_files_download_procedure).  
ES6 imports example: [main.js](src/main/resources/public/main.js).