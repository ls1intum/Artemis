# Server test reference

## Choosing a base class

The bases live in `src/test/java/de/tum/cit/aet/artemis/shared/base/`:

| Base                                            | Use for                                                                                                             |
| ----------------------------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| `AbstractSpringIntegrationIndependentTest`      | The default. A test that needs the Spring context but no CI or version control system.                              |
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

A spy that scheduled tasks or build threads call all the time (`BuildAgentConfiguration`) has no
quiet moment. Mockito keeps the pending stubbing in the mock, so `doReturn(x).when(spy).m()` in a
`@BeforeEach` fails with `WrongTypeOfReturnValue` when such a thread calls any method of the spy
between the two halves. Do not stub or reset it per test:
stub it once when the context is built with answers that read an `AtomicReference`, and change the
holder in the test (`BuildAgentConfigurationOverrides`, `@MockitoSpyBean(reset = MockReset.NONE)`).
Install these answers in a bean post-processor after Mockito creates the spy and before it is injected
into consumers. `SmartInitializingSingleton` is too late: a consumer can schedule work in `@PostConstruct`.
Clear the spy's recorded calls with `Mockito.clearInvocations(...)` after each test to bound its history;
this preserves the startup answers and does not start a new stubbing.

A scheduled task that rewrites shared state a test also writes (the `dockerImageCleanupInfo` map is
cleared and refilled every five minutes) is switched off in the test contexts by its interval
property instead.

Two mechanisms keep background work out of the next test:

- Mail goes through the `mailTaskExecutor`, which is synchronous under the `test` profile. An `@Async`
  method that reaches `MailSendingService` has to name it (`@Async("mailTaskExecutor")`); on the shared
  pool the mail would be sent from a pool thread. `AsyncMailArchitectureTest` rejects such a method.
- `AsyncWorkDrainExtension`, registered on `AbstractArtemisIntegrationTest`, waits after each test
  until every shared `taskExecutor` submission has actually completed, before the `@AfterEach`
  methods reset the spies. `AsyncWorkTrackingConfiguration` wraps the initialized executor before
  consumers receive it and counts work through completion; pool active/queue snapshots cannot
  prove completion because a worker can be paused between dequeueing and execution. A task that does not finish within 30 seconds is logged and the context is no
  longer awaited, so look for that warning when a test leaves a task behind.

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
