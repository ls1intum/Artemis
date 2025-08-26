******************
Server Development
******************

General Best Practices
======================

* Always use the least possible access level; prefer private over public (package-private or protected can be used as well).
* Avoid using ``@Transactional`` randomly. Transactions can hurt performance, introduce locking/concurrency issues, and add complexity. See: https://codete.com/blog/5-common-spring-transactional-pitfalls/
* Define a constant if the same value is used more than once. This makes future changes easier.
* Facilitate code reuse. Move duplicated code to reusable methods. Use Generics where appropriate. IntelliJ can help find and extract duplicates.
* Always qualify a static class member reference with its class name, not with a reference or expression of that class's type.
* Prefer primitive types to classes (e.g. ``long`` instead of ``Long``).
* Use ``./gradlew spotlessCheck`` and ``./gradlew spotlessApply`` to check and fix Java code style.
* Don't use ``.collect(Collectors.toList())``. Use ``.toList()`` for an unmodifiable list or ``.collect(Collectors.toCollection(ArrayList::new))`` for a new ArrayList.


1. Project structure & Naming
=============================

1.1. Folder structure
---------------------

The main application is stored under ``/src/main`` and separated into modules:

* resources - scripts, config files and templates.
    * config - different configurations (production, development, etc.) for application.
        * liquibase - contains ``master.xml`` file where all the changelogs from the changelog folder are specified.
                      When you want to do changes to the database, you will need to add a new changelog file here.
                      To understand how to create new changelog file you can check existing changelog files or read documentation: https://www.liquibase.org/documentation/databasechangelog.html.
* java - Artemis Spring Boot application is located here. It contains the following folders:
    * config - different classes for configuring database, Sentry, Liquibase, etc.
    * **domain** - all the entities and data classes are located here (the model of the server application).
    * **dto** - contains Data Transfer Objects (DTOs) that are used to transfer data between the server and the client.
    * exception - store custom types of exceptions here. We encourage to create custom exceptions to help other developers understand what problem exactly happened.
                  This can also be helpful when we want to provide specific exception handling logic.
    * **repository** - used to access or change objects in the database. There are several techniques to query database: named queries, queries with SpEL expressions and Entity Graphs.
    * security - contains different POJOs (simple classes that don't implement/extend any interface/class and don't have annotations) and component classes related to security.
    * **service** - represents the controller of the server application. Add the application logic here. Retrieve and change objects using repositories.
    * **web** - contains REST and websocket controllers that act as the view of the server application. Validate input and security here, but do not include complex application logic. For websockets, use the ``MessagingTemplate`` to push small data to the client or to notify the client about events.

1.2. Naming convention
----------------------

All methods and classes should use camelCase style. The only difference: the first letter of any class should be capitalized. Most importantly, use intention-revealing, pronounceable names.
Variable names should also use camelCase style, where the first letter should be lowercase. For constants, i.e. arguments with the ``static final`` keywords, use all uppercase letters with underscores to separate words: SCREAMING_SNAKE_CASE.
The only exception to this rule is for the logger, which should be named ``log``.
Variable and constant names should also be intention-revealing and pronounceable.

Example:

.. code-block:: java

    public class ExampleClass {
        private static final Logger log = LoggerFactory.getLogger(ExampleClass.class);

        private static final int MAXIMUM_NUMBER_OF_STUDENTS = 10;

        private final ExampleService exampleService;

        public void exampleMethod() {
            int numberOfStudents = 0;
            [...]
        }
    }


2. Code Organization & Principles
=================================

2.1. Single responsibility principle
------------------------------------

One class and one method should be responsible for only one action, it should do it well and do nothing else. Reduce coupling, if the method does two or three different things at a time then we should consider splitting the functionality.

2.2. Small methods
------------------

There is no standard pattern for method length among the developers. Someone can say 5, in some cases even 20 lines of code is okay. Just try to make methods as small as possible.

2.3. Duplication
----------------

Avoid code duplication. If we cannot reuse a method elsewhere, then the method is probably bad and we should consider a better way to write this method. Use Abstraction to abstract common things in one place.

2.4. Variables and methods declaration
--------------------------------------

* Encapsulate the code you feel might change in future.
* Make variables and methods private by default and increase access step by step by changing them from a private to package-private or protected first and not public right away.
* Classes, methods or functions should be open for extension and closed for modification (open closed design principle).
* Program for the interface and not for implementation, you should use interface type on variables, return types of a method or argument type of methods. Just like using SuperClass type to store object rather using SubClass.
* The use of interface is to facilitate polymorphism, a client should not implement an interface method if its not needed.
* Type inference of variables - var vs. actual type:
    * Variables with primitive types like int, long, or also String should be defined with the actual type by default.
    * Types which share similar functionality but require different handling should also be explicitly stated, e.g. Lists and Sets.
    * Variable types which are untypically long and would decrease readability when writing can be shortened with ``var`` (e.g. custom DTOs).

2.5. Structure your code correctly
----------------------------------

* Default packages are not allowed. It can cause particular problems for Spring Boot applications that use the ``@ComponentScan``, ``@EntityScan`` or ``@SpringBootApplication`` annotations since every class from every jar is read.
* All variables in the class should be declared at the top of the class.
* If a variable is used only in one method then it would be better to declare it as a local variable of this method.
* Methods should be declared in the same order as they are used (from top to bottom).
* More important methods should be declared at the top of a class and minor methods at the end.

2.6. Comments
-------------

* Add JavaDoc and inline comments to clarify code and intent.
* Use AI tools to assist, but always review for accuracy.
* Comments should be in English and add value.

2.7. Keep it simple and stupid
------------------------------

* Don't write complex code.
* Don't write code when you are tired or in a bad mood.
* Optimization vs Readability: always write code that is simple to read and which will be understandable for developers. Because the time and resources spent on hard-to-read code cost much more than what we gain through optimization.
* Commit messages should describe both what the commit changes and how it does it.
* ARCHITECTURE FIRST: writing code without thinking of the system's architecture is useless, in the same way as dreaming about your desires without a plan of achieving them.


3. Database & Persistence
=========================

3.1. Database
-------------

* Write performant queries that can also deal with more than 1000 objects in a reasonable time.
* Prefer one query that fetches additional data instead of many small queries, but don't overdo it. A good rule of thumb is to query not more than 3 associations at the same time.
* Think about lazy vs. eager fetching when modeling the data types. Generally avoid ``fetch = FetchType.EAGER``.
* Do NOT use nested queries, because those hava a bad performance, in particular for many objects.
* Simple datatypes: immediately think about whether ``null`` should be supported as additional state or not. In most cases it is preferable to avoid ``null``.
* Use ``Datetime`` instead of ``Timestamp``. ``Datetime`` occupies more storage space compared to ``Timestamp``, however it covers a greater date range that justifies its use in the long run. Always use ``datetime(3)``

For detailed database guidelines, please refer to the :doc:`./database` page.

3.2. File handling
------------------

* Never use operating system (OS) specific file paths such as "test/test". Always use OS independent paths.
* Do not deal with File.separator manually. Instead use the Path.of(firstPart, secondPart, ...) method which deals with separators automatically.
* Existing paths can easily be appended with a new folder using ``existingPath.resolve(subfolder)``

3.3. Proper annotation of SQL query parameters
----------------------------------------------

Query parameters for SQL must be annotated with ``@Param("variable")``!

Do **not** write

.. code-block:: java

    @Query("""
            SELECT r
            FROM Result r
                LEFT JOIN FETCH r.feedbacks
            WHERE r.id = :resultId
            """)
    Optional<Result> findByIdWithEagerFeedbacks(Long resultId);

but instead annotate the parameter with @Param:

.. code-block:: java

    @Query("""
            SELECT r
            FROM Result r
                LEFT JOIN FETCH r.feedbacks
            WHERE r.id = :resultId
            """)
    Optional<Result> findByIdWithEagerFeedbacks(@Param("resultId") Long resultId);

The string name inside must match the name of the variable exactly!

3.4. SQL statement formatting
-----------------------------

We prefer to write SQL statements all in upper case. Split queries onto multiple lines using the Java Text Blocks notation (triple quotation mark):

.. code-block:: java

    @Query("""
            SELECT r
            FROM Result r
                LEFT JOIN FETCH r.feedbacks
            WHERE r.id = :resultId
            """)
    Optional<Result> findByIdWithEagerFeedbacks(@Param("resultId") Long resultId);

3.5. Do NOT use Sub-queries
---------------------------

SQL statements which do not contain sub-queries are preferable as they are more readable and have a better performance.
So instead of:

.. code-block:: java

    @Query("""
            SELECT COUNT (DISTINCT p)
            FROM StudentParticipation p
            WHERE p.exercise.id = :exerciseId
                AND EXISTS (SELECT s
                    FROM Submission s
                    WHERE s.participation.id = p.id
                        AND s.submitted = TRUE
                    )
            """)
    long countByExerciseIdSubmitted(@Param("exerciseId") long exerciseId);


you should use:

.. code-block:: java

    @Query("""
            SELECT COUNT (DISTINCT p)
            FROM StudentParticipation p
                JOIN p.submissions s
            WHERE p.exercise.id = :exerciseId
                AND s.submitted = TRUE
            """)
    long countByExerciseIdSubmitted(@Param("exerciseId") long exerciseId);

Functionally both queries extract the same result set, but the first one is less efficient as the sub-query is calculated for each StudentParticipation.

3.6. Criteria Builder
---------------------

The Criteria Builder is a powerful feature in JPA/Hibernate that allows you to construct type-safe, dynamic queries in Java code, rather than using string-based JPQL or SQL.
Use Criteria Builder when you need to build queries dynamically at runtime, require type safety, or want to avoid hardcoding query strings.
It is especially useful for complex search/filtering scenarios where query structure depends on user input or other runtime conditions.

For more details, please visit the :doc:`./criteria-builder` page.


4. Utility & Configuration
==========================

4.1. Utility
------------

Utility methods can and should be placed in a class named for specific functionality, not "miscellaneous stuff related to project". Most of the time, our static methods belong in a related class.

4.2. Auto configuration
-----------------------

Spring Boot favors Java-based configuration.
Although it is possible to use Sprint Boot with XML sources, it is generally not recommended.
You don't have to put all your ``@Configuration`` into a single class.
The ``@Import`` annotation can be used to import additional configuration classes.
One of the flagship features of Spring Boot is its use of Auto-configuration. This is the part of Spring Boot that makes your code simply work.
It gets activated when a particular jar file is detected on the classpath. The simplest way to make use of it is to rely on the Spring Boot Starters.

4.3. Dependency injection
-------------------------

* Some of you may argue with this, but by favoring constructor injection you can keep your business logic free from Spring. Not only is the @Autowired annotation optional on constructors, you also get the benefit of being able to easily instantiate your bean without Spring.
* Use setter based DI only for optional dependencies.
* Avoid circular dependencies, try constructor and setter based DI for such cases.


5. REST API & Controllers
=========================

5.1. Keep your ``@RestController``’s clean and focused
------------------------------------------------------

* RestControllers should be stateless.
* RestControllers are by default singletons.
* RestControllers should not execute business logic but rely on delegation to ``@Service`` classes.
* RestControllers should deal with the HTTP layer of the application, handle access control, input data validation, output data cleanup (if necessary) and error handling.
* RestControllers should be oriented around a use-case/business-capability.
* RestControllers must always return DTOs that are as small as possible (please focus on data economy to improve performance and follow data privacy principles).

Route naming conventions:

* Always use kebab-case (e.g. ".../exampleAssessment" → ".../example-assessment").
* The routes should follow the general structure list-entity > entityId > sub-entity ... (e.g. "exercises/{exerciseId}/participations").
* Use plural for a route's list-entities (e.g. "exercises/..."), use singular for a singleton (e.g. ".../assessment"), use verbs for naming remote methods on the server (e.g. ".../submit").
* Specify the key entity at the end of the route (e.g. "text-editor/participations/{participationId}" should be changed to "participations/{participationId}/text-editor").
* Use consistent routes that start with ``courses``, ``exercises``, ``participations``, ``exams`` or ``lectures`` to simplify access control. Do not start routes with other entity names.
* When defining a new route, all subroutes should be addressable as well, e.g. your new route is "exercises/{exerciseId}/statistics", then both "exercises/{exerciseId}" and "exercises" should be addressable.
* If you want an alternative representation of the entity that e.g. sends extra data needed for assessment, then specify the reason for this alternative route at the end of the route, for example "participations/{participationId}/for-assessment".

Additional notes on the controller methods:

* The REST Controllers route should end with a tailing "/" and not start with a "/" (e.g. "api/"), the individual endpoints routes should not start and not end with a "/" (e.g. "exercises/{exerciseId}").
* Use ...ElseThrow alternatives of all Repository and AuthorizationCheck calls whenever applicable, this increases readability (e.g. ``findByIdElseThrow(...)`` instead of ``findById(...)`` and then checking for ``null``).
* POST should return the newly created entity.
* POST should be used to trigger remote methods (e.g. ".../{participationId}/submit" should be triggered with a POST).
* Verify that API endpoints perform appropriate authorization and authentication consistent with the rest of the code base.
    * Always use the Authorization enforcement logic described down below to only allow certain roles to access the method.
    * Perform additional security checks using the ``AuthorizationCheckService``.
* Check for other common weaknesses, e.g., weak configuration, malicious user input, missing log events, etc.
* Never trust user input and check if the passed data exists in the database.
    * Verify the consistency of user input by e.g. checking ids in body and path to see if they match, comparing course in the `RequestBody` with the one referenced by id in the path.
    * Check for user input consistency first, then check the authorization, if e.g. the ids of the course in body and path don't match, the user may be INSTRUCTOR in one course and just a USER in another, this may lead to unauthorized access.
* REST Controller should only handle authentication, error handling, input validation and output creation, the actual logic behind an endpoint should happen in the respective `Service` or `Repository`.
* Handle exceptions and errors with a standard response. Errors are very important in REST APIs. They inform clients that something went wrong, after all.
* Always use different response status codes to notify the client about errors on the server, e.g.:
    * Forbidden - the user is not authorized to access the controller.
    * Bad Request - the request was wrong.
    * Not Found - can't find the requested data or it should be not accessible yet.

.. _server-guideline-dto-usage:

5.2. Use DTOs for Efficient Data Transfer
-----------------------------------------

Purpose of DTOs
^^^^^^^^^^^^^^^

Data Transfer Objects (DTOs) are pivotal in the efficient transfer of data from the server to the client, specifically for the responses from RestControllers and messages via WebSocket. These objects are designed to streamline the data exchange process by ensuring data is immutable, relevant, and precisely tailored to the needs of the client application.

Guidelines for Implementing DTOs
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

1. **Immutable Java Records**: Implement DTOs as Java records to guarantee immutability. While Java records preclude inheritance, resulting in potential duplication, this is considered acceptable in the context of DTOs to ensure data integrity and simplicity.

2. **Primitive data types and composition**: DTOs should strictly encapsulate primitive data types, their corresponding wrapper classes, enums, or compositions of other DTOs. This exclusion of entity objects from DTOs ensures that data remains decoupled from the database entities, facilitating a cleaner and more secure data transfer mechanism.

3. **Minimum necessary data**: Adhere to the principle of including only the minimal data required by the client within DTOs. This practice reduces the overall data footprint, enhances performance, and mitigates the risk of inadvertently exposing unnecessary or sensitive data.

4. **Single responsibility principle**: Each DTO should be dedicated to a specific task or subset of data. Avoid the temptation to reuse DTOs across different data payloads unless the data is identical. This approach maintains clarity and purpose within the data transfer objects.

5. **Simplicity over complexity**: Refrain from embedding methods or business logic within DTOs. Their role is to serve as straightforward data carriers without additional functionalities that could complicate their structure or purpose.

Implications of Not Using DTOs
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Neglecting the use of DTOs can lead to the transmission of excessive or irrelevant data to clients. This not only imposes unnecessary strain on network and system resources but also heightens the risk of exposing sensitive information leading to data privacy issues. A typical example is a direct message chat application where, in the absence of DTOs, a single message might inadvertently include excessive metadata, user details, or other unintended information:


.. code-block:: json

    {
        "notificationType": "conversation",
        "id": 90,
        "title": "artemisApp.conversationNotification.title.newMessage",
        "text": "artemisApp.conversationNotification.text.newMessageDirect",
        "textIsPlaceholder": true,
        "placeholderValues": "[\"PR Testing Course\",\"Test\",\"2023-07-24T03:07:59.299591+02:00[Europe/Berlin]\",\"artemis_test_user_1 artemis_test_user_1\",\"artemis_test_user_1 artemis_test_user_1\",\"oneToOneChat\"]",
        "notificationDate": "2023-07-24T03:07:59.416129+02:00",
        "target": "{\"message\":\"new-message\",\"entity\":\"message\",\"mainPage\":\"courses\",\"id\":31,\"course\":2,\"conversation\":31}",
        "priority": "MEDIUM",
        "outdated": false,
        "author": {
            "id": 2,
            "createdDate": "2023-06-20T17:32:21.249Z",
            "login": "artemis_test_user_1",
            "firstName": "artemis_test_user_1",
            "lastName": "artemis_test_user_1",
            "email": "artemis_test_user_1@example.com",
            "activated": true,
            "langKey": "en",
            "resetDate": "2023-06-20T17:32:21.214Z",
            "groups": ["artemis-athena-students", "artemis-students"],
            "authorities": [{
                "name": "ROLE_USER"
            }],
            "name": "artemis_test_user_1 artemis_test_user_1",
            "participantIdentifier": "artemis_test_user_1",
            "internal": true,
            "deleted": false
        },
        "message": {
            "id": 31,
            "author": {
                "id": 2,
                "name": "artemis_test_user_1 artemis_test_user_1"
            },
            "creationDate": "2023-07-24T03:07:59.299591+02:00",
            "content": "Test",
            "visibleForStudents": true,
            "conversation": {
                "type": "oneToOneChat",
                "id": 31,
                "creator": {
                    "id": 1,
                    "createdDate": "2023-06-20T17:30:31.555Z",
                    "login": "artemis_admin",
                    "firstName": "Administrator",
                    "lastName": "Administrator",
                    "email": "admin@localhost",
                    "activated": true,
                    "langKey": "en",
                    "resetDate": "2023-06-20T17:30:31.495Z",
                    "name": "Administrator Administrator",
                    "participantIdentifier": "artemis_admin",
                    "internal": true,
                    "deleted": false
                },
                "creationDate": "2023-07-24T02:43:54.791+02:00",
                "lastMessageDate": "2023-07-24T03:07:59.372553+02:00"
            },
            "displayPriority": "NONE",
            "resolved": false,
            "answerCount": 0,
            "voteCount": 0
        },
        "conversation": {
            "type": "oneToOneChat",
            "id": 31,
            "creator": {
                "id": 1,
                "createdDate": "2023-06-20T17:30:31.555Z",
                "login": "artemis_admin",
                "firstName": "Administrator",
                "lastName": "Administrator",
                "email": "admin@localhost",
                "activated": true,
                "langKey": "en",
                "resetDate": "2023-06-20T17:30:31.495Z",
                "name": "Administrator Administrator",
                "participantIdentifier": "artemis_admin",
                "internal": true,
                "deleted": false
            },
            "creationDate": "2023-07-24T02:43:54.791+02:00",
            "lastMessageDate": "2023-07-24T03:07:59.372553+02:00"
        },
        "targetTransient": {
            "message": "new-message",
            "entity": "message",
            "mainPage": "courses",
            "id": 31,
            "course": 2,
            "conversation": 31
        }
    }

Hence, entity objects must not be included in DTOs. This is a bad example for a DTO, since it contains the entity object ``Post``:

.. code-block:: java

    public record PostDTO(Post post, MetisCrudAction action) {}

This is a good example for a DTO, because it only contains very little information in the form of boxed primitive types and an enum value:

.. code-block:: java

    public record GradeDTO(String gradeName, Boolean isPassingGrade, GradeType gradeType) {}

5.3. REST endpoint best practices for authorization
---------------------------------------------------

To reject unauthorized requests as early as possible, Artemis employs two solutions:

#. Implicit pre- and post-authorization annotations:
    #.  ``AllowedTools(ToolTokenType.__)``, which ensures that tool-based requests can only access specific endpoints following the Principle of Least Privilege.
    #. ``EnforceRoleInResource`` (e.g. ``EnforceAtLeastInstructorInCourse``) annotations are responsible for blocking users with *wrong or missing authorization roles* without querying the database.
    #. If necessary, these annotations check for access rights to individual resources within the database via light-weight queries.
    #. Currently we offer the following annotations: ``EnforceRoleInCourse`` and ``EnforceRoleInExercise``
#. Explicit authorization checks (which operate in two steps):
    #. ``EnforceAtLeastRole`` (e.g. ``EnforceAtLeastInstructor``) annotations are responsible for blocking users with wrong or missing authorization roles without querying the database.
    #. The ``AuthorizationCheckService`` is responsible for checking access rights to individual resources by querying the database. *Important*: these checks have to be performed explicitly.

Because the first solution (Implicit pre- and post-authorization) increases maintainability and is faster in most cases, always annotate your REST endpoints with the corresponding ``EnforceRoleInResource`` annotation. Always use the annotation for the minimum role that has access.

Artemis distinguishes between six different roles: ADMIN, INSTRUCTOR, EDITOR, TA (teaching assistant/tutor), USER and ANONYMOUS.
Each of the roles has the all the access rights of the roles following it, e.g. ANONYMOUS has almost no rights, while ADMIN users can access every page.

The table contains all annotations for the corresponding minimum role including the required path prefix for all their endpoints and the package they should reside in. Different annotations get used during migration.

+------------------+----------------------------------------+--------------------------+----------------------+
| **Minimum Role** | **Endpoint Annotation**                | **Path Prefix**          | **Package**          |
+------------------+----------------------------------------+--------------------------+----------------------+
| ADMIN            | @EnforceAdmin                          | /api/{module}/admin/     | {module}.web.admin   |
+------------------+----------------------------------------+--------------------------+----------------------+
| INSTRUCTOR       | @EnforceAtLeastInstructor              | /api/{module}/           | {module}.web         |
+------------------+----------------------------------------+--------------------------+----------------------+
| EDITOR           | @EnforceAtLeastEditor                  | /api/{module}/           | {module}.web         |
+------------------+----------------------------------------+--------------------------+----------------------+
| TA               | @EnforceAtLeastTutor                   | /api/{module}/           | {module}.web         |
+------------------+----------------------------------------+--------------------------+----------------------+
| USER             | @EnforceAtLeastStudent                 | /api/{module}/           | {module}.web         |
+------------------+----------------------------------------+--------------------------+----------------------+
| ANONYMOUS        | @EnforceNothing                        | /api/{module}/public/    | {module}.web.open    |
+------------------+----------------------------------------+--------------------------+----------------------+

If, for some reason, you need to deviate from these rules, use ``@ManualConfig``. Use this annotation only if absolutely necessary as it will exclude the endpoint from the automatic authorization tests.

Tool-Based Authorization Annotations
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
To enforce minimal access for external tools, Artemis provides an additional annotation ``@AllowedTools``.
This annotation is used to restrict Tool Tokens to certain endpoints.
A Tool Token is a normal jwt token with the claim ``"tools": "TOOLTYPE"`` specified.

**How it works?**

* **Requests without a tool claim** (e.g., requests from users in a browser) can access all endpoints as long as they meet role-based authorization rules. So they are not restricted by the ``@AllowedTools`` annotation.
* **Requests with a tool claim** (e.g., ``{"tool": "SCORPIO"}`` in the JWT) can only access endpoints annotated with ``@AllowedTools(ToolTokenType.__)`` (e.g. ``@AllowedTools(ToolTokenType.SCORPIO)``).
* If a tool tries to access an unannotated endpoint, it receives a 403 Forbidden response.

**When to Use It?**

Use ``@AllowedTools`` to restrict what tools can do, without limiting normal requests.
For example, an endpoint that provides a Scorpio-specific (VSCode Extension) integration:

.. code-block:: java

    @AllowedTools(ToolTokenType.SCORPIO)
    public ResponseEntity<CourseForDashboardDTO> getCourseForDashboard(@PathVariable long courseId) {
        [...]
        return ResponseEntity.ok(courseForDashboardDTO);
    }

**Best Practices**

* Requests without a tool claim are unrestricted, meaning users and standard API clients will not be affected.
* Tool-based requests must explicitly be allowed by annotating endpoints with ``@AllowedTools(ToolTokenType.VSCODE)``.
* Nevertheless, try to follow the **Principle of Least Privilege** and use tool tokens whenever possible.
* If multiple tools should be allowed for one endpoint, list them:

.. code-block:: java

    @AllowedTools({ToolTokenType.SCORPIO, ToolTokenType.ANDROID})

**How to Get Tool Tokens**
    #. Verify that the tool type is already defined in ``ToolTokenType.java``
    #. Send a POST request to the endpoint ``{{base_url}}/api/core/public/authenticate?tool=TOOLTYPE``


Implicit pre- and post-authorization annotations
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The following example makes the call only accessible to ADMIN and INSTRUCTOR users and then checks the access rights to the course in the database:

Do **not** write

.. code-block:: java

    @EnforceAtLeastInstructor
    public ResponseEntity<Void> enableLearningPathsForCourse(@PathVariable long courseId) {
        var course = courseRepository.findById(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        [...]
        return ResponseEntity.ok().build();
    }

Instead, use the following annotation:

.. code-block:: java

    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<Void> enableLearningPathsForCourse(@PathVariable long courseId) {
        [...]
        return ResponseEntity.ok().build();
    }

Explicit authorization checks
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

CAUTION: Be aware that this solution should be used only in those two cases:
    #. when you need to load user **AND** the resource anyway,
    #. when no matching ``EnforceRoleInResource`` annotation exists.

Always annotate your REST endpoints with the annotation for the minimum role that has access.

The following example makes the call only accessible to ADMIN and INSTRUCTOR users:

.. code-block:: java

    @EnforceAtLeastInstructor
    public ResponseEntity<Void> enableLearningPath(@PathVariable long courseId) {
        var course = courseRepository.findById(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        [...]
        return ResponseEntity.ok().build();
    }

If a user passes the pre-authorization, the access to individual resources like courses and exercises still has to be checked. For example, a user can be a teaching assistant in one course, but only a student in another.
However, do not fetch the user from the database yourself (unless you need to re-use the user object), but only hand a role to the ``AuthorizationCheckService``:

.. code-block:: java

        // If we pass 'null' instead of a user here, the service will fetch the user object
        // and check if the user has at least the given role and access to the resource
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, null);

To reduce duplication, do not add explicit checks for authorization or existence of an entity but always use the ``AuthorizationCheckService``:

.. code-block:: java

    @GetMapping("courses/{courseId}/programming-exercises")
    @EnforceAtLeastTutor
    public ResponseEntity<List<ProgrammingExercise>> getActiveProgrammingExercisesForCourse(@PathVariable Long courseId) {
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);

        List<ProgrammingExercise> exercises = programmingExerciseService.findActiveExercisesByCourseId(courseId);
        return ResponseEntity.ok().body(exercises);
    }

The course repository call takes care of throwing a ``404 Not Found`` exception if there exists no matching course. The ``AuthorizationCheckService`` throws a ``403 Forbidden`` exception if the user with the given role is unauthorized. Afterwards delegate to a service or repository method. The code becomes much shorter, cleaner and more maintainable.


5.4. JSON serialization and deserialization
-------------------------------------------

Always use ObjectMapper (Jackson) and do not use other libraries.


6. Service & Dependency Best Practices
======================================

6.1. Avoid service dependencies
-------------------------------

In order to achieve low coupling and high cohesion, services should have as few dependencies on other services as possible:

* Avoid cyclic and redirectional dependencies
* Do not break the dependency cycle manually or by using `@Lazy`
* Move simple service methods into the repository as ``default`` methods

An example for a simple method is finding a single entity by ID:

.. code-block:: java

    default Course findByIdWithLecturesElseThrow(long courseId) {
        return getValueElseThrow(findWithEagerLecturesById(courseId), courseId);
    }


This approach has several benefits:

* Repositories don't have further dependencies (they are facades for the database), therefore there are no cycles
* We don't need to check for an ``EntityNotFoundException`` in the service since we throw in the repository already
* The "ElseThrow" suffix at the end of the method name makes the behavior clear to outside callers

In general everything changing small database objects can go into the repository. More complex operations have to be done in the service.

Another approach is moving objects into the domain classes, but be aware that you need to add ``@JsonIgnore`` where necessary:

.. code-block:: java

    @JsonIgnore
    default boolean isLocked() {
        if (this instanceof ProgrammingExerciseStudentParticipation) {
            [...]
        }
        return false;
    }
