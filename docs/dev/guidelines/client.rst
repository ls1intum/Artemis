******
Client
******

WORK IN PROGRESS

0. General
==========

The Artemis client is an Angular project. Keep https://angular.io/guide/styleguide in mind.

Some general aspects:

* Never invoke methods from the html template. The automatic change tracking in Angular will kill the application performance
* The Artemis client uses lazy loading to keep the initial bundle size below 2 MB.
* Code quality and test coverage are important. Try to reuse code and avoid code duplication. Write meaningful tests!

1. Names
========

1. Use PascalCase for type names.
2. Do not use "I" as a prefix for interface names.
3. Use PascalCase for enum values.
4. Use camelCase for function names.
5. Use camelCase for property names and local variables.
6. Do not use "_" as a prefix for private properties.
7. Use whole words in names when possible.

2. Components
=============

1. 1 file per logical component (e.g. parser, scanner, emitter, checker).
2. Do not add new files. :)
3. files with ".generated.*" suffix are auto-generated, do not hand-edit them.

3. Types
========

1. Do not export types/functions unless you need to share it across multiple components.
2. Do not introduce new types/values to the global namespace.
3. Shared types should be defined in 'types.ts'.
4. Within a file, type definitions should come first.

4. ``null`` and ``undefined``
=============================

1. Use **undefined**. Do not use null.

5. General Assumptions
======================

1. Consider objects like Nodes, Symbols, etc. as immutable outside the component that created them. Do not change them.
2. Consider arrays as immutable by default after creation.

6. Comments
============

1. Use JSDoc style comments for functions, interfaces, enums, and classes.

7. Strings
============

1. Use single quotes for strings.
2. All strings visible to the user need to be localized (make an entry in the corresponding ``*.json`` file).

8. Style
========

1. Use arrow functions over anonymous function expressions.
2. Always surround arrow function parameters.
    For example, ``x => x + x`` is wrong but the following are correct:

    1. ``(x) => x + x``
    2. ``(x,y) => x + y``
    3. ``<T>(x: T, y: T) => x === y``

3. Always surround loop and conditional bodies with curly braces. Statements on the same line are allowed to omit braces.
4. Open curly braces always go on the same line as whatever necessitates them.
5. Parenthesized constructs should have no surrounding whitespace.
    A single space follows commas, colons, and semicolons in those constructs. For example:

    1. ``for (var i = 0, n = str.length; i < 10; i++) { }``
    2. ``if (x < 10) { }``
    3. ``function f(x: number, y: string): void { }``

6. Use a single declaration per variable statement (i.e. use ``var x = 1; var y = 2;`` over ``var x = 1, y = 2;``).
7. ``else`` goes on the same line from the closing curly brace.
8. Use 4 spaces per indentation.

We use ``prettier`` to style code automatically and ``eslint`` to find additional issues.
You can find the corresponding commands to invoked those tools in ``package.json``.

9. Testing
===========

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

          it('should make get request', async () => {
              const returnedFromApi = {some: 'data'};

              component.callServiceMethod()
                  .subscribe((data) => expect(data).toMatchObject({body: returnedFromApi}));

              const req = httpMock.expectOne({ method: 'GET' });
              req.flush(JSON.stringify(returnedFromApi));
          });
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

10. Preventing Memory Leaks
===========================

It is crucial that you try to prevent memory leaks in both your components and your tests.

What are memory leaks?
**********************

A very good explanation that you should definitely read to understand the problem: https://auth0.com/blog/four-types-of-leaks-in-your-javascript-code-and-how-to-get-rid-of-them/

In essence:

*  JS is a garbage collected language
*  Modern garbage collectors improve on this algorithm in different ways, but the essence is the same: **reachable pieces of memory are marked as such and the rest is considered garbage.**
*  Unwanted references are references to pieces of memory that the developer knows he or she won't be needing
   anymore but that for some reason are kept inside the tree of an active root. **In the context of JavaScript, unwanted references are variables kept somewhere in the code that will not be used anymore and point to a piece of memory that could otherwise be freed.**

What are common reasons for memory leaks?
*****************************************
https://auth0.com/blog/four-types-of-leaks-in-your-javascript-code-and-how-to-get-rid-of-them/:

*  Accidental global variables
*  Forgotten timers or callbacks
*  Out of DOM references
*  Closures

https://making.close.com/posts/finding-the-cause-of-a-memory-leak-in-jest
Mocks not being restored after the end of a test, especially when it involves global objects.

https://www.twilio.com/blog/prevent-memory-leaks-angular-observable-ngondestroy
RXJS subscriptions not being unsubscribed.

What are ways to identify memory leaks?
*****************************************
**Number 1:** Manually checking the heap usage and identifying heap dumps for causes of memory leaks
https://chanind.github.io/javascript/2019/10/12/jest-tests-memory-leak.html

Corresponding commands from the article for our project (enter in the root directory of the project):

.. code-block:: text

   node --expose-gc ./node_modules/.bin/jest --runInBand --logHeapUsage --config ./src/test/javascript/jest.config.js --env=jsdom

.. code-block:: text

   node --inspect-brk --expose-gc ./node_modules/.bin/jest --runInBand --logHeapUsage --config ./src/test/javascript/jest.config.js --env=jsdom

A live demonstration of this technique to find the reason for memory leaks in the GitLab repository: https://www.youtube.com/watch?v=GOYmouFrGrE

**Number 2:** Using the experimental leak detection feature from jest


.. code-block:: text

   --detectLeaks **EXPERIMENTAL**: Detect memory leaks in tests.
                                   After executing a test, it will try to garbage collect the global object used,
                                   and fail if it was leaked [boolean] [default: false]

  --runInBand, -i Run all tests serially in the current process
    (rather than creating a worker pool of child processes that run tests). This is sometimes useful for debugging, but such use cases are pretty rare.



Navigate into src/test/javascript and run either

.. code-block:: text

   jest --detectLeaks --runInBand

or

.. code-block:: text

   jest --detectLeaks


11. Defining Routes and Breadcrumbs
===================================

The ideal schema for routes is that every variable in a path is preceeded by a unique path segment: ``\entityA\:entityIDA\entityB\:entityIDB``  

For example, ``\courses\:courseId\:exerciseId`` is not a good path and should be written as ``\courses\:courseId\exercises\:exerciseId``.
Doubling textual segments like ``\lectures\statistics\:lectureId`` should be avoided and instead formulated as ``\lectures\:lectureId\statistics``.

When creating a completely new route you will have to register the new paths in ``navbar.ts``. A static/textual url segment gets a translation string assigned in the ``mapping`` table. Due to our code-style guidelines any ``-`` in the segment has to be replaced by a ``_``. If your path includes a variable, you will have to add the preceeding path segment to the ``switch`` statement inside the ``addBreadcrumbForNumberSegment`` method.

.. code-block:: ts

	const mapping = {
		courses: 'artemisApp.course.home.title',
		lectures: 'artemisApp.lecture.home.title',
		// put your new directly translated url segments here
		// the index is the path segment in which '-' have to be replaced by '_'
		// the value is the translation string
		your_case: 'artemisApp.cases.title',
	};

	addBreadcrumbForNumberSegment(currentPath: string, segment: string): void {
		switch (this.lastRouteUrlSegment) {
			case 'course-management':
				// handles :courseId
				break;
			case 'lectures':
				// handles :lectureId
				break;
			case 'your-case':
				// add a case here for your :variable which is preceeded in the path by 'your-case'
				break;
		}
	}

12. Strict Template Check
===================================

To prevent errors for strict template rule in TypeScript, Artemis uses following approaches.

Use ArtemisTranslatePipe instead of TranslatePipe
**************************************************
- placeholder="{{ 'global.form.newpassword.placeholder' | translate }}"
+ placeholder="{{ 'global.form.newpassword.placeholder' | artemisTranslate }}"

Use ArtemisTimeAgoPipe instead of TimeAgoPipe
*********************************************
- <span [ngbTooltip]="submittedDate | artemisDate">{{ submittedDate | amTimeAgo }}</span>
+ <span [ngbTooltip]="submittedDate | artemisDate">{{ submittedDate | artemisTimeAgo }}</span>

Some parts of these guidelines are adapted from https://github.com/microsoft/TypeScript-wiki/blob/master/Coding-guidelines.md
