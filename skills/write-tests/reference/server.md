# Server test reference

## Choosing a base class

The bases live in `src/test/java/de/tum/cit/aet/artemis/shared/base/`:

| Base                                            | Use for                                                                                                             |
| ----------------------------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| `AbstractSpringIntegrationIndependentTest`      | The default. A test that needs the Spring context but no CI or version control backend.                             |
| `AbstractSpringIntegrationIndependentBatchTest` | Same context, but batched under a shared `@ResourceLock`, so tests using it never run concurrently with each other. |
| `AbstractSpringIntegrationLocalCILocalVCTest`   | Needs the embedded git server and local CI.                                                                         |
| `AbstractSpringIntegrationJenkinsLocalVCTest`   | Needs the Jenkins connector.                                                                                        |

Each module's `*TestArchitectureTest` declares which bases its integration tests may extend, so the
choice is not free. Look at the module's own architecture test before picking one.

## The admin naming rule

`AbstractModuleTestArchitectureTest.integrationTestsShouldExtendAbstractModuleIntegrationTest`
requires every class in a module whose name ends in `IntegrationTest` to extend one of the bases
that module declares.

In the admin module those are `AbstractSpringIntegrationIndependentBatchTest` and
`AbstractSpringIntegrationLocalCILocalVCTest`
(`src/test/java/de/tum/cit/aet/artemis/admin/architecture/AdminTestArchitectureTest.java`). The
batch base carries `@ResourceLock("AbstractSpringIntegrationIndependentBatchTest")`.

The consequence: a test that mutates global state and needs to run in isolation cannot be called
`*IntegrationTest` in that module, because the name alone puts it in a shared lock group with every
other batch test. Name it `*Test` and extend `AbstractSpringIntegrationIndependentTest`.

The failure mode if you get this wrong is not a clear message. It is either an architecture test
failure naming a base class, or intermittent cross-test interference.

## Dates

PostgreSQL stores timestamps as UTC and does not preserve the offset through a round-trip. Compare
`ZonedDateTime` values with `toInstant()`:

```java
assertThat(actual.getDueDate().toInstant()).isEqualTo(expected.getDueDate().toInstant());
```

Comparing the `ZonedDateTime` values directly passes locally in one timezone and fails in another.

## Shared spies and background threads

A `UnfinishedStubbingException` raised inside `@BeforeEach` means a shared spy was touched by a
background `@Async` thread while the test was setting up its stubs.

The fix is in the background path, not in the test. Making the test wait longer, or re-ordering the
stubbing, moves the race rather than removing it. Find what is still running from the previous test
and stop it deterministically.

## Coverage

CI enforces coverage thresholds per module. A new class with no test can fail the build even when
every existing test passes. Check the module's threshold before assuming a change needs no test.

## Running the suite while working

Do not run a build in the same worktree while the full suite is running. A concurrent
`spotlessApply` or `compileJava` invalidates the running build's classpath and produces scattered
failures that look like real ones but start mid-run.

For the same reason, a `clean` from a second Gradle invocation wipes the test classpath underneath
an in-flight run. If failures start appearing partway through a previously healthy run, suspect a
concurrent build before suspecting the code.
