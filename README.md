# Snakes

- sources are located in `src/`

- project uses gradle to build

- project uses Protocol Buffer ([link](https://gitlab.ccfit.nsu.ru/vadipp/04_snakes_task/-/blob/master/src/main/protobuf/snakes.proto))

# Compile and build jar

```
$ .\gradlew jar 
```
jar archive will be built in `build/libs/snakes.jar`.

It can be run with
```
$ java -jar build/libs/snakes.jar
```
