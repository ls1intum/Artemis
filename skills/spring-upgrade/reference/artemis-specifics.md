# Artemis specifics for a Spring upgrade

Current version numbers are not repeated here. Read them in the files named below, together with the
comments next to them, which give the reason for each pin.

## Where versions are set

| What                                                                             | Where                                              |
| -------------------------------------------------------------------------------- | -------------------------------------------------- |
| Spring Boot, Spring Cloud train and most shared versions                         | `gradle.properties`                                |
| Spring Cloud modules, pinned one by one                                          | `build.gradle`                                     |
| Spring AI bill of materials                                                      | `build.gradle`                                     |
| Jackson 2, Jackson 3 and OpenTelemetry bills of materials                        | `build.gradle`, block `dependencyManagement`       |
| Guards for libraries that must move together                                     | `gradle/dependency-families.gradle`                |
| Netty and OpenTelemetry, forced on every configuration                           | `build.gradle`, `resolutionStrategy`               |
| `strictly` pins and constraints                                                  | `build.gradle`, block `dependencies`               |
| Libraries with their version in the declaration, such as `spring-security-lti13` | `build.gradle`, block `dependencies`               |
| Forces for the build script classpath                                            | `build.gradle`, block `buildscript`                |
| Version properties also read by other scripts                                    | `gradle/openapi.gradle`, `gradle/liquibase.gradle` |
| Gradle itself                                                                    | `gradle/wrapper/gradle-wrapper.properties`         |

## Rules for pinned versions

- **Spring Cloud.** Each Boot minor needs its release train, and each pinned module must equal the
  version the train declares. A mismatch is no startup failure: the compatibility verifier shuts the
  server down shortly after it started.
- **Overrides of managed versions**, such as logback, Tomcat and Hibernate. An override also replaces
  a newer version that a later Boot release manages. Compare them on every upgrade.
- **Jackson.** The order of the imports matters. After a minor upgrade, run the passkey, LTI, Spring AI and
  static code analysis parser tests.
- **Apache HttpComponents** client5 and core5 move together. A mismatched pair once broke cluster
  formation while the build stayed green. Other watched families are listed in the guard file.
- **testcontainers-redis** imports its own Boot version, which the GitHub dependency graph reports.
- **Libraries declared explicitly because code imports them.** Do not rely on a transitive dependency,
  and never import a shaded package: `testNoShadedDependencies` in `ArchitectureTest` rejects it.

## Temporary workarounds

Check each condition on every upgrade.

| Workaround                                                                                                      | Remove when                                                                                                                                                                                     |
| --------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `src/main/java/de/tum/cit/aet/artemis/core/config/BeanNamesByTypeCacheWorkaround.java`, a bean factory deadlock | The Framework release contains the fix. The class comment lists everything to delete. A test fails as soon as it does not deadlock any more. A newer JDK than 25 may refuse its reflection      |
| Two replacement resolvers in `src/main/java/de/tum/cit/aet/artemis/lti/config/CustomLti13Configurer.java`       | `spring-security-lti13` releases a version for the new Framework, its version in `build.gradle` is raised to that release, and `Lti13InitiationIntegrationTest` passes without the replacements |
| Pins in `src/main/java/de/tum/cit/aet/artemis/core/config/ArtemisJacksonDefaults.java`                          | The comment on the pin is satisfied, `JacksonSerializationContractTest` has a case for the pin (add a missing one, for example for UTC dates) and passes without it, and the client is migrated |
| `commons-codec` held back in `build.gradle`                                                                     | The condition next to it is met and `SAML2MetadataCompatibilityTest` passes                                                                                                                     |
| `src/main/java/de/tum/cit/aet/artemis/core/config/EurekaClientConfiguration.java`, copied set-up code           | Keep it, but compare it with the new Spring Cloud Netflix release. A difference may show only across nodes                                                                                      |

`src/main/resources/META-INF/spring.factories` registers further extension points. Check that each
interface named there still exists and is still called.

## Settings and defaults

- **Keep the properties migrator.** Generic upgrade guides remove `spring-boot-properties-migrator`
  after migrating. Artemis ships it on purpose with `spring.properties.migrator.fail: on-error`, so
  that a key which Spring or a library marks as removed, or as deprecated without a replacement of
  the same type, stops the start instead of being ignored. Some of these keys still work. A renamed
  key with such a replacement only causes a warning, and a misspelled or unknown key without this
  metadata is not reported. A renamed or removed key needs an entry in the upgrade guide.
- **New Spring AI model types** are created unless switched off, and need credentials. The server ends
  when one of these beans fails.
- `SpringAIAutoConfigurationFilter` always excludes the OpenAI model auto-configurations, and all
  other Spring AI ones while no module uses them, so failures there appear only with Hyperion, Atlas
  or the AI worker enabled.

## Guard tests

Run these tests first. They cover problems that compiling does not show. Run them with
`./gradlew test --tests <name> -x webapp`.

| Test                                        | Catches                                                |
| ------------------------------------------- | ------------------------------------------------------ |
| `MainConfigurationPropertiesMigrationTest`  | A production setting that no longer binds              |
| `SpringAiModelTypesConfigurationTest`       | A new Spring AI model type that is not switched off    |
| `SpringAIAutoConfigurationFilterTest`       | The auto-configuration filter                          |
| `BeanNamesByTypeCacheWorkaroundTest`        | Whether the deadlock workaround is still needed        |
| `BeanNamesByTypeCacheWorkaroundContextTest` | The workaround is active in the application            |
| `TomcatConfigurationTest`                   | Changed Tomcat behaviour                               |
| `JacksonSerializationContractTest`          | Changed JSON payloads                                  |
| `Lti13InitiationIntegrationTest`            | Moodle and other LTI logins                            |
| `PasskeyIntegrationTest`                    | Passkey registration and login                         |
| `LocalVCIntegrationTest`                    | Git access                                             |
| `HazelcastDistributedDataTest`              | Distributed data                                       |
| `RedissonDistributedDataTest`               | Distributed data on Redis and Valkey                   |
| `SpringFactoriesRegistrationTest`           | A `spring.factories` entry that Spring stopped reading |
| `SAML2MetadataCompatibilityTest`            | SAML2 identity provider metadata                       |

## Earlier upgrade failures

| Pull request | Failure                                                                                          | What to check                                                       |
| ------------ | ------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------- |
| #12769       | After Boot 4, every LTI login failed with `NoSuchMethodError` in a third-party library           | When one call into a library fails, check every use of that library |
| #12909       | A Cloud train mismatch shut the server down after the start                                      | Keep watching the server after it reports that it has started       |
| #12909       | An empty YAML object `{}` was bound as text and broke every SAML2 test                           | Run the tests of every authentication method                        |
| #14716       | The Boot tooling extracted `Artemis.jar` instead of `Artemis.war`, so the Docker image failed    | Check `deployment/docker/artemis/Dockerfile` and the Helm templates |
| #14716       | A test started a second application in the shared test JVM and silenced all later log assertions | Do not start a second application inside the shared test JVM        |

## Deprecations recorded as follow-ups

Noted with Boot 4.2, check whether they are done: RestTemplate is deprecated for removal in
Framework 8.0 (move to `RestClient`), and the Spring AI chat memory types are deprecated for removal in
Spring AI 3.0.
