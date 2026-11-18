## Programming Exercise Template Projects

This document describes details about the creation and changing the
templates that are used to generate programming exercises for the supported
programming languages and project types in Artemis.

**Note**: The creation of this documentation is still in progress. Therefore,
some parts may be missing and will be extended in the future.

## Java
### Updating to the Newest Ares Version
In order to update to the newest version of Ares, you have to change
the version number described in the respective pom.xml or build.gradle.
This process is described in the [Ares documentation](https://github.com/ls1intum/Ares#what-you-need-to-do-outside-ares).

### Changing the Version of the Gradle Wrapper
In order to change to the newest version of Gradle, you have to change the
version number for the following three files:
1. [Template Repository gradle-wrapper.properties](java/gradle_gradle/exercise/gradle/wrapper/gradle-wrapper.properties)
2. [Solution Repository gradle-wrapper.properties](java/gradle_gradle/solution/gradle/wrapper/gradle-wrapper.properties)
3. [Test Repository gradle-wrapper.properties](java/test/gradle/projectTemplate/gradle/wrapper/gradle-wrapper.properties)

In addition, you have to update the gradle-wrapper.jar. This can
be done by creating a project in the local IDE with the corresponding gradle wrapper version
and executing `gradlew` from the command line. This will generate the required files. This newly
generated wrapper file has to be replaced with the following three files:
1. [Template Repository gradle-wrapper.jar](java/gradle_gradle/exercise/gradle/wrapper/gradle-wrapper.jar)
2. [Solution Repository gradle-wrapper.jar](java/gradle_gradle/solution/gradle/wrapper/gradle-wrapper.jar)
3. [Test Repository gradle-wrapper.jar](java/test/gradle/projectTemplate/gradle/wrapper/gradle-wrapper.jar)
