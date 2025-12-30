************
Client Tests
************

**If you are new to client testing, it is highly recommended that you work through the** `testing part <https://angular.dev/guide/testing>`_ **of the angular tutorial.**

Most client tests use `Jest <https://jestjs.io>`__ as the testing framework.
We use `NgMocks <https://www.npmjs.com/package/ng-mocks/>`_ for mocking the dependencies of an angular component.

.. note::
   **Vitest for Zoneless Modules**: Modules migrated to Angular's zoneless change detection use
   `Vitest <https://vitest.dev/>`__ instead of Jest. The authoritative list of Vitest modules is defined by the
   ``include`` patterns in ``vitest.config.ts``.

   **Running Vitest tests:**

   - Watch mode: ``npm run vitest``
   - Single run: ``npm run vitest:run``
   - With coverage: ``npm run vitest:coverage``

   **Key differences from Jest:**

   - Use ``vi.spyOn()`` instead of ``jest.spyOn()``
   - Use ``vi.fn()`` instead of ``jest.fn()``
   - Use ``vi.clearAllMocks()`` instead of ``jest.clearAllMocks()``

You can run all Jest tests by invoking ``npm run testw8`` in the root directory of the Artemis project.
If you want to run individual tests, you can use the following commands:

1. Run all tests in a file: ``npm run test:one -- --test-path-pattern='src/main/webapp/app/fileupload/manage/assess/file-upload-assessment\.component\.spec\.ts$'``
2. Run all tests in a module or folder: ``npm run test:one -- --test-path-pattern='src/main/webapp/app/fileupload/.*$'``

General Test Pattern
====================

The most basic test looks like this:

.. code:: ts

    import { ComponentFixture, TestBed } from '@angular/core/testing';

    describe('SomeComponent', () => {
        let someComponentFixture: ComponentFixture<SomeComponent>;
        let someComponent: SomeComponent;

        beforeEach(async () => {
            await TestBed.configureTestingModule({
                imports: [
                    SomeComponent,
                    MockPipe(SomePipeUsedInTemplate),
                    MockComponent(SomeComponentUsedInTemplate),
                    MockDirective(SomeDirectiveUsedInTemplate),
                ],
                providers: [
                    MockProvider(SomeServiceUsedInComponent),
                ],
            })
                .compileComponents();

            someComponentFixture = TestBed.createComponent(SomeComponent);
            someComponent = someComponentFixture.componentInstance;
        });

        afterEach(() => {
            jest.restoreAllMocks();
        });

        it('should initialize', () => {
            someComponentFixture.detectChanges();
            expect(someComponent).not.toBeUndefined();
        });
    });

Guidelines Overview
===================

The following sections outline **best practices** for writing client tests in Artemis.

1. Test Isolation
=================

* Always test a component in isolation.
* Do not import entire production modules.
* Use **mock pipes, directives, and components** where possible.
* Only use real dependencies if absolutely necessary.
* Prefer `stubs for child components <https://angular.dev/guide/testing/components-scenarios#stubbing-unneeded-components>`_.

**Example: Bad practice**
    .. code:: ts

        describe('ParticipationSubmissionComponent', () => {
            ...

            beforeEach(async () => {
                await TestBed.configureTestingModule({
                    imports: [
                        ArtemisTestModule,
                        NgxDatatableModule,
                        ArtemisResultModule,
                        ArtemisSharedModule,
                        TranslateModule.forRoot(),
                        ParticipationSubmissionComponent,
                        MockComponent(UpdatingResultComponent),
                        MockComponent(AssessmentDetailComponent),
                        MockComponent(ComplaintsForTutorComponent),
                    ],
                    providers: [
                        provideRouter([]),
                    ],
                })
                    .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
                    .compileComponents();
            });
        });

    Imports large modules → test runtime: 313.931s:

    .. code-block:: text

       PASS  src/test/javascript/spec/component/participation-submission/participation-submission.component.spec.ts (313.931 s, 625 MB heap size)


**Example: Good practice**
    .. code:: ts

            describe('ParticipationSubmissionComponent', () => {
                ...

                beforeEach(async () => {
                    await TestBed.configureTestingModule({
                        imports: [
                            ArtemisTestModule,
                            NgxDatatableModule,
                            ParticipationSubmissionComponent,
                            MockComponent(UpdatingResultComponent),
                            MockComponent(AssessmentDetailComponent),
                            MockComponent(ComplaintsForTutorComponent),
                            MockTranslateValuesDirective,
                            MockPipe(ArtemisTranslatePipe),
                            MockPipe(ArtemisDatePipe),
                            MockPipe(ArtemisTimeAgoPipe),
                            MockDirective(DeleteButtonDirective),
                            MockComponent(ResultComponent),
                        ],
                        providers: [
                            provideRouter([]),
                        ],
                    })
                        .compileComponents();
                });
            });

    Mocks dependencies → test runtime: 13.685s:

    .. code-block:: text

       PASS  src/test/javascript/spec/component/participation-submission/participation-submission.component.spec.ts (13.685 s, 535 MB heap size)

    Now the whole testing suite is running **~25 times faster**!

Here are the improvements for the test above:

    * **Removed** production module imports:

    .. code-block:: text

        - ArtemisResultModule
        - ArtemisSharedModule
        - TranslateModule.forRoot()

    * **Mocked** pipes, directives and components that are not supposed to be tested:

    .. code-block:: text

        + MockTranslateValuesDirective
        + MockPipe(ArtemisTranslatePipe)
        + MockPipe(ArtemisDatePipe)
        + MockPipe(ArtemisTimeAgoPipe)
        + MockDirective(DeleteButtonDirective)
        + MockComponent(ResultComponent)
        + MockComponent(FaIconComponent)

More examples on test speed improvement can be found in the `following PR <https://github.com/ls1intum/Artemis/pull/3879/files>`_.


2. Mocking Rules
================

* **Services**:
    * Mock services if they just return data from the server.
    * If the service has important logic → keep the real service but mock **HTTP requests and responses** instead.
    * This allows us to test the interaction of the component with the service and in addition test that the service logic works correctly. A good explanation can be found in the `official angular documentation <https://angular.dev/guide/http/testing>`_.

    .. code:: ts

            import { provideHttpClient } from '@angular/common/http';
            import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
            describe('SomeComponent', () => {
                beforeEach(() => {
                    TestBed.configureTestingModule({
                        imports: [...],
                        providers: [
                            provideHttpClient(),
                            provideHttpClientTesting(),
                        ],
                    });

                    ...
                    httpMock = TestBed.inject(HttpTestingController);
                });

                afterEach(() => {
                    ...
                    httpMock.verify();
                    jest.restoreAllMocks();
                });

                it('should make get request', fakeAsync(() => {
                    const returnedFromApi = {some: 'data'};

                    component.callServiceMethod()
                        .subscribe((data) => expect(data.body).toEqual(returnedFromApi));

                    const req = httpMock.expectOne({ method: 'GET', url: 'urlThatMethodCalls' });
                    req.flush(returnedFromApi);
                    tick();
                }));
            });

* **Never use** ``NO_ERRORS_SCHEMA`` (`angular documentation <https://angular.dev/guide/testing/components-scenarios#noerrorsschema>`_). Use stubs/mocks instead.

* **Reset mocks** with ``jest.restoreAllMocks()`` in ``afterEach``.
    * This is important if they get defined across multiple tests
    * Only works if mocks are created with ``jest.spyOn``.
    * Avoid manually assigning ``jest.fn()``.


3. Coverage & Quality
=====================

* Ensure at least **80% line coverage**.
    * Run ``npm test`` for coverage report.
    * Or `run the tests in IntelliJ IDEA with coverage activated <https://www.jetbrains.com/help/idea/running-test-with-coverage.html>`_.

* Prefer **user-interaction tests** instead of testing internal methods directly.
    * Example: If you have a component that loads and displays some data when the user clicks a button, you should query for that button, simulate a click, and then assert that the data has been loaded and that the expected template changes have occurred.
    * Here is an example of such a test for `exercise-update-warning component <https://github.com/ls1intum/Artemis/blob/6e44346c77ce4c817e24269f0150b4118bc12f50/src/test/javascript/spec/component/shared/exercise-update-warning.component.spec.ts#L32-L46>`_

    .. code:: ts

        it('should trigger saveExerciseWithoutReevaluation once', () => {
            const emitSpy = jest.spyOn(comp.confirmed, 'emit');
            const saveExerciseWithoutReevaluationSpy = jest.spyOn(comp, 'saveExerciseWithoutReevaluation');

            const button = fixture.debugElement.nativeElement.querySelector('#save-button');
            button.click();

            fixture.detectChanges();

            expect(saveExerciseWithoutReevaluationSpy).toHaveBeenCalledOnce();
            expect(emitSpy).toHaveBeenCalledOnce();
        });

* **Do not** use ``overrideTemplate()`` to remove templates.
    * The template is part of the component and must be tested.
    * Do not do this:

    .. code:: ts

        describe('SomeComponent', () => {
            let someComponentFixture: ComponentFixture<SomeComponent>;
            let someComponent: SomeComponent;

            beforeEach(async () => {
                await TestBed.configureTestingModule({
                    imports: [SomeComponent],
                    providers: [
                        ...
                    ],
                })
                    .overrideTemplate(SomeComponent, '') // DO NOT DO THIS
                    .compileComponents();

                someComponentFixture = TestBed.createComponent(SomeComponent);
                someComponent = someComponentFixture.componentInstance;
            });
        });

4. Naming Test Doubles
======================

Use clear terminology for test doubles:

* ``Spy`` → observes calls, no replacement.
* ``Mock`` → spy + returns custom values for specific inputs.
* ``Stub`` → spy + returns fixed values regardless of input.

Example:

.. code:: ts

    const clearSpy = jest.spyOn(component, 'clear');
    const getNumberStub = jest.spyOn(component, 'getNumber').mockReturnValue(42);


5. Expectations
===============

* Make expectations as **specific** as possible.
  * ``expect(value).toBe(5)`` is better than ``expect(value).not.toBeUndefined()``.

* Always follow ``expect`` with a matcher.
    * Use meaningful, specific assertions instead of generic booleans.
    * Extract as much as possible from the `expect` statement
    * For example, instead of

    .. code:: ts

        expect(course == undefined).toBeTrue();
        expect(courseList).toHaveLength(4);

    extract as much as possible:

    .. code:: ts

        expect(course).toBeUndefined();
        expect(courseList).toHaveLength(4);

* If you have minimized :code:`expect`, use the verification function that provides the most meaningful error message in case the verification fails. Use matchers from `Jest <https://jestjs.io/docs/expect>`_ and `Jest Extended <https://jest-extended.jestcommunity.dev/docs/matchers>`_.

* Use a **uniform style** for common cases to keep the codebase as consistent as possible:

  +--------------------------------------------------------+-----------------------------------------------------------------+
  | Situation                                              | Solution                                                        |
  +========================================================+=================================================================+
  | Expecting a boolean value                              | :code:`expect(value).toBeTrue();`                               |
  |                                                        | :code:`expect(value).toBeFalse();`                              |
  +--------------------------------------------------------+-----------------------------------------------------------------+
  | Two objects should be the same reference               | :code:`expect(object).toBe(referenceObject);`                   |
  +--------------------------------------------------------+-----------------------------------------------------------------+
  | A CSS element should exist                             | :code:`expect(element).not.toBeNull();`                         |
  |                                                        |                                                                 |
  | A CSS element should not exist                         | :code:`expect(element).toBeNull();`                             |
  +--------------------------------------------------------+-----------------------------------------------------------------+
  | A value should be undefined                            | :code:`expect(value).toBeUndefined();`                          |
  +--------------------------------------------------------+-----------------------------------------------------------------+
  | A value should be either null or undefined             | Use :code:`expect(value).toBeUndefined();` for internal calls.  |
  |                                                        |                                                                 |
  |                                                        | If an external library uses null value, use                     |
  |                                                        | :code:`expect(value).toBeNull();` and if not avoidable          |
  |                                                        | :code:`expect(value).not.toBeNull();`.                          |
  |                                                        |                                                                 |
  |                                                        | **Never use** :code:`expect(value).not.toBeDefined()`           |
  |                                                        | or :code:`expect(value).toBeNil()` as they might not catch all  |
  |                                                        | failures under certain conditions.                              |
  +--------------------------------------------------------+-----------------------------------------------------------------+
  | A class object should be defined                       | Always try to test for certain properties or entries.           |
  |                                                        |                                                                 |
  |                                                        | :code:`expect(classObject).toContainEntries([[key, value]]);`   |
  |                                                        |                                                                 |
  |                                                        | :code:`expect(classObject).toEqual(expectedClassObject);`       |
  |                                                        |                                                                 |
  |                                                        | **Never use** :code:`expect(value).toBeDefined()` as            |
  |                                                        | it might not catch all failures under certain conditions.       |
  +--------------------------------------------------------+-----------------------------------------------------------------+
  | A class object should not be undefined                 | Try to test for a defined object as described above.            |
  +--------------------------------------------------------+-----------------------------------------------------------------+
  | A spy should not have been called                      | :code:`expect(spy).not.toHaveBeenCalled();`                     |
  +--------------------------------------------------------+-----------------------------------------------------------------+
  | A spy should have been called once                     | :code:`expect(spy).toHaveBeenCalledOnce();`                     |
  +--------------------------------------------------------+-----------------------------------------------------------------+
  | A spy should have been called with a value             | Always test the number of calls as well:                        |
  |                                                        |                                                                 |
  |                                                        | .. code:: ts                                                    |
  |                                                        |                                                                 |
  |                                                        |     expect(spy).toHaveBeenCalledOnce();                         |
  |                                                        |     expect(spy).toHaveBeenCalledWith(value);                    |
  |                                                        |                                                                 |
  |                                                        | If you have multiple calls, you can verify the parameters       |
  |                                                        | of each call separately:                                        |
  |                                                        |                                                                 |
  |                                                        | .. code:: ts                                                    |
  |                                                        |                                                                 |
  |                                                        |     expect(spy).toHaveBeenCalledTimes(3);                       |
  |                                                        |     expect(spy).toHaveBeenNthCalledWith(1, value0);             |
  |                                                        |     expect(spy).toHaveBeenNthCalledWith(2, value1);             |
  |                                                        |     expect(spy).toHaveBeenNthCalledWith(3, value2);             |
  +--------------------------------------------------------+-----------------------------------------------------------------+
