******
Client Tests
******

**If you are new to client testing, it is highly recommended that you work through the testing part of the angular tutorial:** https://angular.io/guide/testing

We use Jest (https://jestjs.io/) as our client testing framework.

There are different tools available to support client testing. A common combination you can see in our codebase is:

- Sinon (https://sinonjs.org/) for creating test spies, stubs and mocks
- Chai (https://www.chaijs.com/) with Sinon Chai (https://github.com/domenic/sinon-chai) for assertions.
- NgMocks (https://www.npmjs.com/package/ng-mocks) for mocking the dependencies of an angular component.

The most basic test looks similar to this:

 .. code:: ts

    import * as chai from 'chai';
    import * as sinonChai from 'sinon-chai';
    import * as sinon from 'sinon';
    import { ComponentFixture, TestBed } from '@angular/core/testing';

    chai.use(sinonChai);
    const expect = chai.expect;

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
                schemas: [],
            })
                .compileComponents()
                .then(() => {
                    someComponentFixture = TestBed.createComponent(SomeComponent);
                    someComponent = someComponentFixture.componentInstance;
                });
        });

        afterEach(function () {
            sinon.restore();
        });

        it('should initialize', () => {
            someComponentFixture.detectChanges();
            expect(SomeComponent).to.be.ok;
        });
    });

Some guidelines:

1. A component should be tested in isolation without any dependencies if possible. Do not simply import the whole production module. Only import real dependencies if it is essential for the test
   that the real dependency is used. Instead mock pipes, directives and components that the component under test depends upon. A very useful technique is writing stubs for child components: https://angular.io/guide/testing-components-scenarios#stubbing-unneeded-components.
   This has the benefit of being able to test the interaction with the child components.

Example of a bad test practice:

    .. code:: ts

        describe('ParticipationSubmissionComponent', () => {
            ...

            beforeEach(() => {
                return TestBed.configureTestingModule({
                    imports: [ArtemisTestModule, NgxDatatableModule, ArtemisResultModule, ArtemisSharedModule, TranslateModule.forRoot(), RouterTestingModule, MomentModule],
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

Running time:

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

Running time:
.. code-block:: text

   PASS  src/test/javascript/spec/component/participation-submission/participation-submission.component.spec.ts (13.685 s, 535 MB heap size)

Now the whole testing suite is running **~25 times faster**!
Here are the improvements for the test above:

- **Removed** production module imports:

   - ArtemisResultModule
   - ArtemisSharedModule
   - TranslateModule.forRoot()
   - MomentModule

- **Mocked** pipes, directives and components that are not supposed to be tested

   - MockTranslateValuesDirective
   - MockPipe(ArtemisTranslatePipe)
   - MockPipe(ArtemisDatePipe)
   - MockPipe(ArtemisTimeAgoPipe)
   - MockDirective(DeleteButtonDirective)
   - MockComponent(AlertComponent)
   - MockComponent(ResultComponent)
   - MockComponent(FaIconComponent)

More examples on test speed improvement can be found in the following PR: https://github.com/ls1intum/Artemis/pull/3879/files

   *  Services should be mocked if they simply return some data from the server. However, if the service has some form of logic included (for exampling converting dates to moments),
      and this logic is important for the component, do not mock the service methods, but mock the http requests and responses from the api. This allows us to test the interaction
      of the component with the service and in addition test that the service logic works correctly. A good explanation can be found in the official angular documentation: https://angular.io/guide/http#testing-http-requests

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
          });

          it('should make get request', fakeAsync( () => {
              const returnedFromApi = {some: 'data'};

              component.callServiceMethod()
                  .subscribe((data) => expect(data.body).toEqual(returnedFromApi));

              const req = httpMock.expectOne({ method: 'GET', url: 'urlThatMethodCalls' });
              req.flush(returnedFromApi);
              tick();
          }));
        });






2. Do not overuse ``NO_ERRORS_SCHEMA`` (https://angular.io/guide/testing-components-scenarios#no_errors_schema).
   This tells angular to ignore the attributes and unrecognized elements, prefer to use component stubs as mentioned above.

3. When using sinon, use sandboxes (https://sinonjs.org/releases/latest/sandbox/).
   Sandboxes remove the need to keep track of every fake created, which greatly simplifies cleanup and improves readability.
   Since ``sinon@5.0.0``, the sinon object is a default sandbox. Unless you have a very advanced setup or need a special configuration, you probably want to only use that one.

4. Make sure to have at least 80% test coverage. Running ``yarn test --coverage`` to create a coverage report. You can also simply run the tests in IntelliJ IDEA with coverage activated.

5. It is preferable to test a component through the interaction of the user with the template. This decouples the test from the concrete implementation used in the component.
   For example if you have a component that loads and displays some data when the user clicks a button, you should query for that button, simulate a click and then assert that the data has been loaded and that the expected
   template changes have occurred.

6. Do not remove the template during tests. The template is a crucial part of a component and should not be removed during test. Do not do this:


 .. code:: ts

    import * as chai from 'chai';
    import * as sinonChai from 'sinon-chai';
    import * as sinon from 'sinon';

    chai.use(sinonChai);
    const expect = chai.expect;

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
                schemas: [],
            })
                .overrideTemplate(SomeComponent, '') // DO NOT DO THIS
                .compileComponents()
                .then(() => {
                    someComponentFixture = TestBed.createComponent(SomeComponent);
                    someComponent = someComponentFixture.componentInstance;
                });
        });
    });
