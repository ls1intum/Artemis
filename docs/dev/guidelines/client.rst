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
2. Only surround arrow function parameters when necessary.
    For example, ``(x) => x + x`` is wrong but the following are correct:

    1. ``x => x + x``
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

Some parts of these guidelines are adapted from https://github.com/microsoft/TypeScript-wiki/blob/master/Coding-guidelines.md
