# Trevas Lab

[![Trevas Lab CI](https://github.com/InseeFrLab/Trevas-Lab/actions/workflows/ci.yml/badge.svg)](https://github.com/InseeFrLab/Trevas-Lab/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)

Java API to consume [Trevas engine](https://github.com/InseeFr/Trevas).

## Build and run

```shell
mvn package
cd target
java --add-exports java.base/sun.nio.ch=ALL-UNNAMED -jar trevas-lab-1.1.0.jar
```
