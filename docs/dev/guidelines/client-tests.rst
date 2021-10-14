************
Client Tests
************

**If you are new to client testing, it is highly recommended that you work through the** `testing part <https://angular.io/guide/testing>`_ **of the angular tutorial.**

We use `Jest <https://jestjs.io>`_ as our client testing framework.

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
   that the real dependency is used. Instead, use mock pipes, mock directives and mock components that the component under test depends upon. A very useful technique is writing `stubs for child components <https://angular.io/guide/testing-components-scenarios#stubbing-unneeded-components>`_.
   This has the benefit of being able to test the interaction with the child components.

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
                        MockComponent(FaIconComponent),
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

2. Try to make expectations as specific as possible. If you expect a specific result, compare to this result and do not compare to the absence of some arbitrary other value. This ensures that no faulty values you didn't expect can sneak in the code base without the tests failing. For example :code:`toBe(5)` is better than :code:`not.toBeUndefined()`, which would also pass if the value wrongly changes to 6.

3. Do not use ``NO_ERRORS_SCHEMA`` (`link <https://angular.io/guide/testing-components-scenarios#no_errors_schema>`_).
   This tells angular to ignore the attributes and unrecognized elements, prefer to use component stubs as mentioned above.

4. Calling `jest.restoreAllMocks()` ensures that all mocks created with Jest get reset after each test. This is important if they get defined across multiple tests. This will only work if the mocks were created with `jest.spyOn`. Manually assigning `jest.fn()` should be avoided with this configuration.

5. Make sure to have at least 80% line test coverage. Run ``npm test`` to create a coverage report. You can also simply run the tests in IntelliJ IDEA with coverage activated.

6. It is preferable to test a component through the interaction of the user with the template. This decouples the test from the concrete implementation used in the component.
   For example if you have a component that loads and displays some data when the user clicks a button, you should query for that button, simulate a click and then assert that the data has been loaded and that the expected
   template changes have occurred.

Here is an example of a test for `exercise-update-warning component <https://github.com/ls1intum/Artemis/blob/main/src/test/javascript/spec/component/shared/exercise-update-warning.component.spec.ts#L32-L43>`_

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

7. Do not remove the template during tests by making use of ``overrideTemplate()``. The template is a crucial part of a component and should not be removed during test. Do not do this:

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

8. Name the variables properly for test doubles:

.. code:: ts
    const clearSpy = jest.spyOn(someComponent, 'clear');
    const getNumberMock = jest.spyOn(someComponent, 'getNumber').mockReturnValue(42);
