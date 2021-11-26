************
Client Tests
************

**If you are new to client testing, it is highly recommended that you work through the** `testing part <https://angular.io/guide/testing>`_ **of the angular tutorial.**

We use `Jest <https://jestjs.io>`__ as our client testing framework.

There are different tools available to support client testing. We try to limit ourselves to Jest as much as possible. We use `NgMocks <https://www.npmjs.com/package/ng-mocks/>`_ for mocking the dependencies of an angular component.

The most basic test looks similar to this:

.. code:: ts

    import { ComponentFixture, TestBed } from '@angular/core/testing';

    describe('SomeComponent', () => {
        let someComponentFixture: ComponentFixture<SomeComponent>;
        let someComponent: SomeComponent;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [],
                declarations: [
                    SomeComponent,
                    MockPipe(SomePipeUsedInTemplate),
                    MockComponent(SomeComponentUsedInTemplate),
                    MockDirective(SomeDirectiveUsedInTemplate),
                ],
                providers: [
                    MockProvider(SomeServiceUsedInComponent),
                ],
            })
                .compileComponents()
                .then(() => {
                    someComponentFixture = TestBed.createComponent(SomeComponent);
                    someComponent = someComponentFixture.componentInstance;
                });
        });

        afterEach(function () {
            jest.restoreAllMocks();
        });

        it('should initialize', () => {
            someComponentFixture.detectChanges();
            expect(SomeComponent).not.toBeUndefined();
        });
    });

Some guidelines:

1. A component should be tested in isolation without any dependencies if possible. Do not simply import the whole production module. Only import real dependencies if it is essential for the test
   that the real dependency is used. Instead, use mock pipes, mock directives and mock components that the component under test depends upon. A very useful technique is writing `stubs for child components <https://angular.io/guide/testing-components-scenarios#stubbing-unneeded-components>`_. This has the benefit of being able to test the interaction with the child components.

    Example of a bad test practice:

    .. code:: ts

        describe('ParticipationSubmissionComponent', () => {
            ...

            beforeEach(() => {
                return TestBed.configureTestingModule({
                    imports: [ArtemisTestModule, NgxDatatableModule, ArtemisResultModule, ArtemisSharedModule, TranslateModule.forRoot(), RouterTestingModule],
                    declarations: [
                        ParticipationSubmissionComponent,
                        MockComponent(UpdatingResultComponent),
                        MockComponent(AssessmentDetailComponent),
                        MockComponent(ComplaintsForTutorComponent),
                    ],
                    providers: [
                        ...
                    ],
                })
                    .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
                    .compileComponents()
                    .then(() => {
                        ...
                    });
            });
        });

    Running time: 313.931s:

    .. code-block:: text

       PASS  src/test/javascript/spec/component/participation-submission/participation-submission.component.spec.ts (313.931 s, 625 MB heap size)

    Example of a good test practice:

        .. code:: ts

            describe('ParticipationSubmissionComponent', () => {
                ...

                beforeEach(() => {
                    return TestBed.configureTestingModule({
                        imports: [ArtemisTestModule, RouterTestingModule, NgxDatatableModule],
                        declarations: [
                            ParticipationSubmissionComponent,
                            MockComponent(UpdatingResultComponent),
                            MockComponent(AssessmentDetailComponent),
                            MockComponent(ComplaintsForTutorComponent),
                            MockTranslateValuesDirective,
                            MockPipe(ArtemisTranslatePipe),
                            MockPipe(ArtemisDatePipe),
                            MockPipe(ArtemisTimeAgoPipe),
                            MockDirective(DeleteButtonDirective),
                            MockComponent(AlertComponent),
                            MockComponent(ResultComponent),
                        ],
                        providers: [
                            ...
                        ],
                    })
                        .compileComponents()
                        .then(() => {
                            ...
                        });
                });
            });

    Running time: 13.685s:

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
        + MockComponent(AlertComponent)
        + MockComponent(ResultComponent)
        + MockComponent(FaIconComponent)

    More examples on test speed improvement can be found in the `following PR <https://github.com/ls1intum/Artemis/pull/3879/files>`_.

        *  Services should be mocked if they simply return some data from the server. However, if the service has some form of logic included (for example converting dates to datejs instances),
           and this logic is important for the component, do not mock the service methods, but mock the HTTP requests and responses from the API. This allows us to test the interaction
           of the component with the service and in addition test that the service logic works correctly. A good explanation can be found in the `official angular documentation <https://angular.io/guide/http#testing-http-requests>`_.

        .. code:: ts

            import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
            describe('SomeComponent', () => {
                beforeEach(() => {
                    TestBed.configureTestingModule({
                        imports: [HttpClientTestingModule],
                    });

                    ...
                    httpMock = injector.get(HttpTestingController);
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

2. Do not use ``NO_ERRORS_SCHEMA`` (`angular documentation <https://angular.io/guide/testing-components-scenarios#no_errors_schema>`_). This tells angular to ignore the attributes and unrecognized elements, prefer to use component stubs as mentioned above.

3. Calling `jest.restoreAllMocks()` ensures that all mocks created with Jest get reset after each test. This is important if they get defined across multiple tests. This will only work if the mocks were created with `jest.spyOn`. Manually assigning `jest.fn()` should be avoided with this configuration.

4. Make sure to have at least 80% line test coverage. Run ``npm test`` to create a coverage report. You can also simply `run the tests in IntelliJ IDEA with coverage activated <https://www.jetbrains.com/help/idea/running-test-with-coverage.html>`_.

5. It is preferable to test a component through the interaction of the user with the template. This decouples the test from the concrete implementation used in the component.
   For example, if you have a component that loads and displays some data when the user clicks a button, you should query for that button, simulate a click, and then assert that the data has been loaded and that the expected template changes have occurred.

    Here is an example of such a test for `exercise-update-warning component <https://github.com/ls1intum/Artemis/blob/main/src/test/javascript/spec/component/shared/exercise-update-warning.component.spec.ts#L32-L43>`_

    .. code:: ts

        it('should trigger saveExerciseWithoutReevaluation once', () => {
            const emitSpy = jest.spyOn(comp.confirmed, 'emit');
            const saveExerciseWithoutReevaluationSpy = jest.spyOn(comp, 'saveExerciseWithoutReevaluation');

            const button = fixture.debugElement.nativeElement.querySelector('#save-button');
            button.click();

            fixture.detectChanges();

            expect(saveExerciseWithoutReevaluationSpy).toHaveBeenCalledTimes(1);
            expect(emitSpy).toHaveBeenCalled();
        });

6. Do not remove the template during tests by making use of ``overrideTemplate()``. The template is a crucial part of a component and should not be removed during test. Do not do this:

    .. code:: ts

        describe('SomeComponent', () => {
            let someComponentFixture: ComponentFixture<SomeComponent>;
            let someComponent: SomeComponent;

            beforeEach(() => {
                TestBed.configureTestingModule({
                    imports: [],
                    declarations: [
                        SomeComponent,
                    ],
                    providers: [
                    ],
                })
                    .overrideTemplate(SomeComponent, '') // DO NOT DO THIS
                    .compileComponents()
                    .then(() => {
                        someComponentFixture = TestBed.createComponent(SomeComponent);
                        someComponent = someComponentFixture.componentInstance;
                    });
            });
        });

7. Name the variables properly for test doubles:

    .. code:: ts

        const clearSpy = jest.spyOn(someComponent, 'clear');
        const getNumberStub = jest.spyOn(someComponent, 'getNumber').mockReturnValue(42); // This always returns 42

    - `Spy`: Doesn't replace any functionality but records calls
    - `Mock`: Spy + returns a specific implementation for a certain input
    - `Stub`: Spy + returns a default implementation independent of the input parameters.

8. Try to make expectations as specific as possible. If you expect a specific result, compare to this result and do not compare to the absence of some arbitrary other value. This ensures that no faulty values you didn't expect can sneak in the codebase without the tests failing. For example :code:`toBe(5)` is better than :code:`not.toBeUndefined()`, which would also pass if the value wrongly changes to 6.

9. When expecting results use :code:`expect` for client tests. That call **must** be followed by another assertion statement like :code:`toBe(true)`. It is best practice to use more specific expect statements rather than always expecting boolean values. It is also recommended to extract as much as possible from the `expect` statement.

    For example, instead of

    .. code:: ts

        expect(course == undefined).toBe(true);
        expect(courseList.length).toBe(4);

    extract as much as possible:

    .. code:: ts

        expect(course).toBe(undefined);
        expect(courseList).toHaveLength(4);

10. If you have minimized :code:`expect` and can choose between multiple verification functions providing the same functionality, choose the most generic one. This way we will use as few functions as possible. For example prefer :code:`toBe(true)` and :code:`toBe(false)` over :code:`toBeTrue()` and :code:`toBeFalse()`.

11. Use `Jest <https://jestjs.io/docs/expect>`__ whenever possible. Use `Jest Extended <https://github.com/jest-community/jest-extended#api>`_ only if it shortens the expect statements considerably and makes it more readable.

12. For situations described below, only use the uniform solution to keep the codebase as consistent as possible.

  +--------------------------------------------------------+-----------------------------------------------------------------+
  | Situation                                              | Solution                                                        |
  +========================================================+=================================================================+
  | Expecting a boolean value                              | :code:`expect(value).toBe(true);`                               |
  |                                                        |                                                                 |
  |                                                        | :code:`expect(value).toBe(false);`                              |
  +--------------------------------------------------------+-----------------------------------------------------------------+
  | Two objects should be the same reference               | :code:`expect(object).toBe(referenceObject);`                   |
  +--------------------------------------------------------+-----------------------------------------------------------------+
  | A CSS element should exist                             | :code:`expect(element).not.toBe(null);`                         |
  |                                                        |                                                                 |
  | A CSS element should not exists                        | :code:`expect(element).toBe(null);`                             |
  +--------------------------------------------------------+-----------------------------------------------------------------+
  | A value should be undefined                            | :code:`expect(value).toBe(undefined);`                          |
  +--------------------------------------------------------+-----------------------------------------------------------------+
  | A value should be either null or undefined             | Use :code:`expect(value).toBe(undefined);` for internal calls.  |
  |                                                        |                                                                 |
  |                                                        | If an external library uses null value, use                     |
  |                                                        | :code:`expect(value).toBe(null);` and if not avoidable          |
  |                                                        | :code:`expect(value).not.toBe(null);`.                          |
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
  | A spy should have been called once                     | :code:`expect(spy).toHaveBeenCalledTimes(1);`                   |
  +--------------------------------------------------------+-----------------------------------------------------------------+
