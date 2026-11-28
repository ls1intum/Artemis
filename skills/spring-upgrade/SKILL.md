---
name: spring-upgrade
description: Upgrade Spring Boot, Framework, Security, Data, Cloud or Spring AI, or a library version that Artemis overrides, and investigate failures that such an upgrade caused.
---

# Upgrade the Spring stack

A successful compile is not enough to verify a Spring upgrade. Earlier upgrades in Artemis failed at
runtime, for example through a library that calls a removed method, a server that shuts itself down
after it has started, or a deadlock in the bean factory. Work through the steps in order and report
after each one what you found.

Read `reference/artemis-specifics.md` before step 3. It lists what is specific to Artemis and is not in
any Spring document.

## 1. Fix the scope

- Read the current versions from `gradle.properties` and `build.gradle`.
- Take the target versions from the user. Without one, propose the latest releases and ask.
- Move the Spring Cloud release train together with Spring Boot, and check that Spring AI and other
  Spring projects have a release for the target.
- A patch upgrade still needs steps 3 and 5. A minor or major upgrade needs every step.

## 2. Read the official notes

Read the notes of **every** release after the current version up to the target, from the source. Do not
rely on what you remember about a release.

| Project          | Notes                                                                     |
| ---------------- | ------------------------------------------------------------------------- |
| Spring Boot      | https://github.com/spring-projects/spring-boot/wiki and its releases page |
| Spring Framework | https://github.com/spring-projects/spring-framework/wiki                  |
| Spring Security  | https://docs.spring.io/spring-security/reference/whats-new.html           |
| Spring Data      | https://github.com/spring-projects/spring-data-commons/wiki               |
| Spring Cloud     | https://github.com/spring-cloud/spring-cloud-release/wiki                 |
| Spring AI        | https://docs.spring.io/spring-ai/reference/upgrade-notes.html             |
| Hibernate        | https://docs.hibernate.org/orm/current/migration-guide/                   |
| Security fixes   | https://spring.io/security                                                |

A new version may have no wiki page yet. Read the release notes on the project's GitHub releases page
then, also those of the Spring Data and Spring Cloud sub-projects. Security fixes can change behaviour
without appearing in any of these notes.

## 3. Match the notes against Artemis

The notes are incomplete. Also compare the old and the new versions:

- the managed versions in both Spring Boot bills of materials (the POM named in step 4), including
  libraries that jump a major version;
- the configuration metadata in the jars of the changed libraries (`spring-configuration-metadata.json`),
  for keys that are removed, renamed or deprecated. Check every key in the configuration and the
  deployment files against it, also keys that an earlier release deprecated;
- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, for new
  auto-configurations. A new Spring AI one becomes active as soon as an AI module is enabled.

For each change in the notes or in this comparison, search the code, the tests, the configuration and
the deployment files for what it names. Keep the changes that may apply and sort them:

- **Breaking:** the build, the start or a feature fails unless something is changed.
- **Behaviour:** everything still runs, but the result differs: a default, a metric tag, a response
  header. Ask the user for each one whether to accept it or restore the old behaviour.
- **Deprecation:** works now. Record it as follow-up work instead of widening this upgrade.

Search configuration in `src/main/resources/config`, `src/test/resources/config` and
`deployment`. The test configuration shadows the production `application.yml`, and environment
variables in the deployment files use the same keys in capital letters.

For a minor or major upgrade, also find the third-party libraries that were built against Spring. List
them with `./gradlew dependencies --configuration runtimeClasspath`, check that each one supports the
new version, and test the feature that uses it. A removed or missing method fails only when it is first
called.

Show the user the list before changing anything.

## 4. Change the versions

1. Raise the versions where `reference/artemis-specifics.md` says they are set.
2. Compare every override, force and pin with what the new Boot release manages
   (`https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/<version>/spring-boot-dependencies-<version>.pom`)
   **and** with the newest patch release in the library's `maven-metadata.xml` on Maven Central. Being
   ahead of Boot is not enough: raise it to the newest patch. Delete one only when Boot has caught up
   and the comment next to it names no other reason. A deletion must never lower a version, and a
   version property may be read by other Gradle scripts.
3. Check every temporary workaround for its removal condition.
4. Apply the breaking changes, then the behaviour changes the user chose.

## 5. Verify

```bash
./gradlew compileJava compileTestJava -x webapp
./gradlew verifyDependencyFamilies verifyBuildscriptClasspathPins -x webapp
./gradlew test -DincludeTags='ArchitectureTest' -x webapp
```

Then run the guard tests listed in `reference/artemis-specifics.md`, and:

- **Start a server and keep watching it.** "Started ArtemisApp" is not enough. Wait for "Deferred
  eager initialization of all beans completed", and look for "Shutting down the application" and
  `CompatibilityNotMetException`. Start with each optional module that creates beans lazily, since a
  bean that fails then ends the process. See [local-setup](../local-setup/SKILL.md).
- **Run the whole server test suite** in CI, and read failures for new startup or logging behaviour
  before calling them flaky. See [ci-triage](../ci-triage/SKILL.md).
- **Run the full E2E suite, single node and multi-node.** Cluster formation and passkeys fail only
  across nodes. See [e2e-pr-check](../e2e-pr-check/SKILL.md).
- **Build the Docker image** in CI. It depends on what the Boot tooling extracts from the archive.

State which of these you did not run.

## 6. Report

- The version table: before, after and how each is set.
- Applied changes, accepted behaviour changes, open risks and follow-ups.
- Overrides and workarounds removed, kept or added, each added one with its removal condition.
- What an operator must do, written into `documentation/docs/admin/upgrade-guide.mdx`: renamed keys,
  changed defaults, changed metrics.
- The Spring Boot version named in `AGENTS.md`.
- New Artemis-specific findings, added to `reference/artemis-specifics.md` for the next upgrade.

Keep notes and change lists in the chat or the pull request; do not commit plan files.
