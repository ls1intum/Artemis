******
Client
******

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
2. files with ".generated.*" suffix are auto-generated, do not hand-edit them.

3. Types
========

1. Do not export types/functions unless you need to share it across multiple components.
2. Do not introduce new types/values to the global namespace.
3. Shared types should be defined in 'types.ts'.
4. Within a file, type definitions should come first.

4. ``null`` and ``undefined``
=============================

Use **undefined**. Do not use null.

5. General Assumptions
======================

1. Consider objects like Nodes, Symbols, etc. as immutable outside the component that created them. Do not change them.
2. Consider arrays as immutable by default after creation.

6. Comments
============

Use JSDoc style comments for functions, interfaces, enums, and classes.

7. Strings
============

1. Use single quotes for strings.
2. All strings visible to the user need to be localized (make an entry in the corresponding ``*.json`` file).

8. Buttons and Links
====================

1. Be aware that Buttons navigate only in the same tab while Links provide the option to use the context menu or a middle-click to open the page in a new tab. Therefore:
2. Buttons are best used to trigger certain functionalities (e.g. ``<button (click)='deleteExercise(exercise)'>...</button``)
3. Links are best for navigating on Artemis (e.g. ``<a [routerLink]='getLinkForExerciseEditor(exercise)' [queryParams]='getQueryParamsForEditor(exercise)'>...</a>``)

9. Icons with Text
====================

If you use icons next to text (for example for a button or link), make sure that they are separated by a newline. HTML renders one or multiple newlines as a space.

Do this:

.. code-block:: html+ng2

    <fa-icon [icon]="'times'"></fa-icon>
    <span>Text</span>

Don't do one of these or any other combination of whitespaces:

.. code-block:: html+ng2

    <fa-icon [icon]="'times'"></fa-icon><span>Text</span>

    <fa-icon [icon]="'times'"></fa-icon><span> Text</span>
    <fa-icon [icon]="'times'"></fa-icon> <span>Text</span>

    <fa-icon [icon]="'times'"></fa-icon>
    <span> Text</span>

Ignoring this will lead to inconsistent spacing between icons and text.

10. Code Style
==============

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
You can find the corresponding commands to invoke those tools in ``package.json``.

11. Preventing Memory Leaks
===========================

It is crucial that you try to prevent memory leaks in both your components and your tests.

What are memory leaks?
**********************

A very good explanation that you should definitely read to understand the problem: https://auth0.com/blog/four-types-of-leaks-in-your-javascript-code-and-how-to-get-rid-of-them/

In essence:

*  JS is a garbage-collected language
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

   node --expose-gc ./node_modules/.bin/jest --runInBand --logHeapUsage --config ./jest.config.js --env=jsdom

.. code-block:: text

   node --inspect-brk --expose-gc ./node_modules/.bin/jest --runInBand --logHeapUsage --config ./jest.config.js --env=jsdom

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


12. Defining Routes and Breadcrumbs
===================================

The ideal schema for routes is that every variable in a path is preceded by a unique path segment: ``\entityA\:entityIDA\entityB\:entityIDB``

For example, ``\courses\:courseId\:exerciseId`` is not a good path and should be written as ``\courses\:courseId\exercises\:exerciseId``.
Doubling textual segments like ``\lectures\statistics\:lectureId`` should be avoided and instead formulated as ``\lectures\:lectureId\statistics``.

When creating a completely new route you will have to register the new paths in ``navbar.ts``. A static/textual url segment gets a translation string assigned in the ``mapping`` table. Due to our code-style guidelines any ``-`` in the segment has to be replaced by a ``_``. If your path includes a variable, you will have to add the preceding path segment to the ``switch`` statement inside the ``addBreadcrumbForNumberSegment`` method.

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
                // add a case here for your :variable which is preceded in the path by 'your-case'
                break;
        }
    }

13. Strict Template Check
=========================

To prevent errors for strict template rule in TypeScript, Artemis uses following approaches.

Use ArtemisTranslatePipe instead of TranslatePipe
*************************************************
Do not use ``placeholder="{{ 'global.form.newpassword.placeholder' | translate }}"``

Use ``placeholder="{{ 'global.form.newpassword.placeholder' | artemisTranslate }}"``

Use ArtemisTimeAgoPipe instead of TimeAgoPipe
*********************************************
Do not use ``<span [ngbTooltip]="submittedDate | artemisDate">{{ submittedDate | amTimeAgo }}</span>``

Use ``<span [ngbTooltip]="submittedDate | artemisDate">{{ submittedDate | artemisTimeAgo }}</span>``

14. Chart Instantiation
=======================

We are using the framework ngx-charts in order to instantiate charts and diagrams in Artemis.

The following is an example HTML template for a vertical bar chart:

.. code-block:: html+ng2

    <div #containerRef class="col-md-9">
        <ngx-charts-bar-vertical
            [view]="[containerRef.offsetWidth, 300]"
            [results]="ngxData"
            [scheme]="color"
            [legend]="false"
            [xAxis]="true"
            [yAxis]="true"
            [yScaleMax]="20"
            [roundEdges]="true"
            [showDataLabel]="true">
            <ng-template #tooltipTemplate let-model="model">
                {{ labelTitle }}: {{ round((model.value / totalValue) * 100, 1) }}%
            </ng-template>
        </ngx-charts-bar-vertical>
    </div>

Here are a few tips when using this framework:

    1. In order to configure the content of the tooltips in the chart, declare a `ng-template <https://angular.io/api/core/ng-template>`_ with the reference ``#tooltipTemplate``
       containing the desired content within the selector. The framework dynamically recognizes this template. In the example above,
       the tooltips are configured in order to present the percentage value corresponding to the absolute value represented by the bar.
       Depending on the chart type, there is more than one type of tooltip configurable.
       For more information visit https://swimlane.gitbook.io/ngx-charts/

    2. Some design properties are not directly configurable via the framework (e.g. the font-size and weight of the data labels).
       The tool ``::ng-deep`` is useful in these situations as it allows to change some of these properties by overwriting them in
       a corresponding style sheet. Adapting the font-size and weight of data labels would look like this:

       .. code-block:: css

           ::ng-deep .textDataLabel {
               font-weight: bolder;
               font-size: 15px !important;
           }

    3. In order to make the chart responsive in width, bind it to the width of its parent container.
       First, annotate the parent container with a reference (in the example ``#containerRef``).
       Then, when configuring the dimensions of the chart in ``[view]``, insert ``containerRef.offsetWidth`` instead
       of an specific value for the width.

Some parts of these guidelines are adapted from https://github.com/microsoft/TypeScript-wiki/blob/main/Coding-guidelines.md

15. Responsive Layout
=====================

Ensure that the layout of your page or component shrinks accordingly and adapts to all display sizes (responsive design).

Prefer using the ``.container`` class (https://getbootstrap.com/docs/5.2/layout/containers/) when you want limit the page width on extra-large screens.
Do not use the following for this purpose if it can be avoided:

.. code-block:: html

    <div class="row justify-content-center">
        <div class="col-12 col-lg-8">
            <!-- Do not do this -->
        </div>
    </div>
