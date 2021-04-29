******
Server
******

WORK IN PROGRESS

0. Folder structure
===================

The main application is stored under ``/src/main`` and the main folders are:

* resources - script, config files and templates are stored here.
    * config - different configurations (production, development, etc.) for application.
        * liquibase - contains ``master.xml`` file where all the changelogs from the changelog folder are specified.
                      When you want to do some changes to the database, you will need to add a new changelog file here.
                      To understand how to create new changelog file you can check existing changelog files or read documentation: https://www.liquibase.org/documentation/databasechangelog.html.
* java - Artemis Spring Boot application is located here. It contains the following folders:
    * config - different classes for configuring database, Sentry, Liquibase, etc.
    * domain - all the entities and data classes are located here (the model of the server application).
    * exception - store custom types of exceptions here. We encourage to create custom exceptions to help other developers understand what problem exactly happened.
                  This can also be helpful when we want to provide specific exception handling logic.
    * security - contains different POJOs (simple classes that don't implement/extend any interface/class and don't have annotations) and component classes related to security.
    * repository - used to access or change objects in the database. There are several techniques to query database: named queries, queries with SpEL expressions and Entity Graphs.
    * service - represents the controller of the server application. Add the application logic here. Retrieve and change objects using repositories.
    * web - contains two folders:
        * rest - contains REST controllers that act as the view of the server application. Validate input and security here, but do not include complex application logic
        * websocket - contains controllers that handle real-time communication with the client based on the Websocket protocol. Use the ``MessagingTemplate`` to push data to the client or to notify the client about events.

1. Naming convention
====================

All variables, methods and classes should use CamelCase style. The only difference: the first letter of any class should be capital. Most importantly use intention-revealing, pronounceable names.

2. Single responsibility principle
==================================

One method should be responsible for only one action, it should do it well and do nothing else. Reduce coupling, if our method does two or three different things at a time then we should consider splitting the functionality.

3. Small methods
================

There is no standard pattern for method length among the developers. Someone can say 5, in some cases even 20 lines of code is okay. Just try to make methods as small as possible.

4. Duplication
==============

Avoid code duplication. If we cannot reuse a method elsewhere, then the method is probably bad and we should consider a better way to write this method. Use Abstraction to abstract common things in one place.

5. Variables and methods declaration
====================================

* Encapsulate the code you feel might change in future.
* Make variables and methods private by default and increase access step by step by changing them from a private to package-private or protected first and not public right away.
* Classes, methods or functions should be open for extension and closed for modification (open closed design principle).
* Program for the interface and not for implementation, you should use interface type on variables, return types of a method or argument type of methods. Just like using SuperClass type to store object rather using SubClass.
* The use of interface is to facilitate polymorphism, a client should not implement an interface method if its not needed.

6. Structure your code correctly
================================

* Default packages are not allowed. It can cause particular problems for Spring Boot applications that use the ``@ComponentScan``, ``@EntityScan`` or ``@SpringBootApplication`` annotations since every class from every jar is read.
* All variables in the class should be declared at the top of the class.
* If a variable is used only in one method then it would be better to declare it as a local variable of this method.
* Methods should be declared in the same order as they are used (from top to bottom).
* More important methods should be declared at the top of a class and minor methods at the end.

7. Database
===========

* Write performant queries that can also deal with more than 1000 objects in a reasonable time.
* Prefer one query that fetches additional data instead of many small queries, but don't overdo it. A good rule of thumb is to query not more than 3 associations at the same time.
* Think about lazy vs. eager fetching when modeling the data types.
* Only if it is inevitable, use nested queries. You should try use as few tables as possible.
* Simple datatypes: immediately think about whether ``null`` should be supported as additional state or not. In most cases it is preferable to avoid ``null``.
* Use ``Timestamp`` instead of ``Datetime``.

8. Comments
===========

Only write comments for complicated algorithms, to help other developers better understand them. We should only add a comment, if our code is not self-explanatory.

9. Utility
==========

Utility methods can and should be placed in a class named for specific functionality, not "miscellaneous stuff related to project". Most of the time, our static methods belong in a related class.

10. Auto configuration
======================

Spring Boot favors Java-based configuration.
Although it is possible to use Sprint Boot with XML sources, it is generally not recommended.
You don't have to put all your ``@Configuration`` into a single class.
The ``@Import`` annotation can be used to import additional configuration classes.
One of the flagship features of Spring Boot is its use of Auto-configuration. This is the part of Spring Boot that makes your code simply work.
It gets activated when a particular jar file is detected on the classpath. The simplest way to make use of it is to rely on the Spring Boot Starters.

11. Keep your ``@RestController``’s clean and focused
=====================================================

* RestControllers should be stateless.
* RestControllers are by default singletons.
* RestControllers should not execute business logic but rely on delegation.
* RestControllers should deal with the HTTP layer of the application.
* RestControllers should be oriented around a use-case/business-capability.

Route naming conventions:

* Always use kebab-case (e.g. "/exampleAssessment" → "/example-assessment").
* The routes should follow the general structure entity > entityId > sub-entity ... (e.g. "/exercises/{exerciseId}/participations").
* Use plural for a route's entities.
* Specify the key entity at the end of the route (e.g. "text-editor/participations/{participationId}" should be changed to "participations/{participationId}/text-editor").
* Use consistent routes that start with ``courses``, ``exercises`` or ``lectures`` to simplify access control. Do not start routes with other entity names.

Additional notes on the controller methods:

* POST should return the newly created entity
* Verify that API endpoints perform appropriate authorization and authentication consistent with the rest of the code base.
    * Always use ``@PreAuthorize`` to only allow certain roles to access the method.
    * Perform additional security checks using the ``AuthorizationCheckService``.
* Check for other common weaknesses, e.g., weak configuration, malicious user input, missing log events, etc.
* Never trust user input and check if the passed data exists in the database.
* Handle exceptions and errors with a standard response. Errors are very important in REST APIs. They inform clients that something went wrong, after all.
* Always use different response status codes to notify the client about errors on the server, e.g.:
    * Forbidden - the user is not authorized to access the controller.
    * Bad Request - the request was wrong.
    * Not Found - can't find the requested data or it should be not accessible yet.

12. Dependency injection
========================

* Some of you may argue with this, but by favoring constructor injection you can keep your business logic free from Spring. Not only is the @Autowired annotation optional on constructors, you also get the benefit of being able to easily instantiate your bean without Spring.
* Use setter based DI only for optional dependencies.
* Avoid circular dependencies, try constructor and setter based DI for such cases.

13. Keep it simple and stupid
=============================

* Don't write complex code.
* Don't write code when you are tired or in a bad mood.
* Optimization vs Readability: always write code that is simple to read and which will be understandable for developers. Because the time and resources spent on hard-to-read code cost much more than what we gain through optimization
* Commit messages should describe both what the commit changes and how it does it.
* ARCHITECTURE FIRST: writing code without thinking of the system's architecture is useless, in the same way as dreaming about your desires without a plan of achieving them.

14. File handling
=================

* Never use operating system (OS) specific file paths such as "test/test". Always use OS independent paths.
* Do not deal with File.separator manually. Instead use the Paths.get(firstPart, secondPart, ...) method which deals with separators automatically.
* Existing paths can easily be appended with a new folder using ``existingPath.resolve(subfolder)``

15. General best practices
==========================

* Always use the least possible access level, prefer using private over public access modifier (package-private or protected can be used as well).
* Previously we used transactions very randomly, now we want to avoid using ``Transactional``. Transactions can kill performance, introduce locking issues and database concurrency problems, and add complexity to our application. Good read: https://codete.com/blog/5-common-spring-transactional-pitfalls/
* Define a constant if the same value is used more than once. Constants allow you to change code later a lot easier. Instead of looking for the places where this variable was used, you only need to change it in only one place.
* Facilitate code reuse. Always move duplicated code to reusable methods. IntelliJ is very good at suggesting duplicated lines and even automatically extracting them. Also don't be shy to use Generics.
* Always qualify a static class member reference with its class name and not with a reference or expression of that class's type.
* Prefer using primitive types to classes, e.g. ``long`` instead of ``Long``.
* Use ``./gradlew spotlessCheck`` and ``./gradlew spotlessApply`` to check Java code style and to automatically fix it.

16. Avoid service dependencies
==============================

In order to achieve low coupling and high cohesion, services should have as few dependencies on other services as possible:

* Avoid cyclic and redirectional dependencies
* Do not break the dependency cycle manually or by using `@Lazy`
* Move simple service methods into the repository as ``default`` methods

An example for a simple method is finding a single entity by ID:

.. code-block:: java

	default StudentExam findByIdElseThrow(Long studentExamId) throws EntityNotFoundException {
	   return findById(studentExamId).orElseThrow(() -> new EntityNotFoundException("Student Exam", studentExamId));
	}


This approach has several benefits:

* Repositories don't have further dependencies (they are facades for the database), therefore there are no cycles
* We don't need to check for an ``EntityNotFoundException`` in the service since we throw in the repository already
* The "ElseThrow" suffix at the end of the method name makes the behaviour clear to outside callers

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

17. Proper annotation of SQL query parameters
=============================================

Query parameters for SQL must be annotated with ``@Param("variable")``!

Do **not** write

.. code-block:: java

    @Query("""
            SELECT r FROM Result r
            LEFT JOIN FETCH r.feedbacks
            WHERE r.id = :resultId
            """)
    Optional<Result> findByIdWithEagerFeedbacks(Long resultId);

but instead annotate the parameter with @Param:

.. code-block:: java

    @Query("""
            SELECT r FROM Result r
            LEFT JOIN FETCH r.feedbacks
            WHERE r.id = :resultId
            """)
    Optional<Result> findByIdWithEagerFeedbacks(@Param("resultId") Long resultId);

The string name inside must match the name of the variable exactly!

18. SQL statement formatting
============================

We prefer to write SQL statements all in upper case. Split queries onto multiple lines using the Java Text Blocks notation (triple quotation mark):

.. code-block:: java

    @Query("""
            SELECT r FROM Result r
            LEFT JOIN FETCH r.feedbacks
            WHERE r.id = :resultId
            """)
    Optional<Result> findByIdWithEagerFeedbacks(@Param("resultId") Long resultId);

19. Avoid the usage of Sub-queries
==================================

SQL statements which do not contain sub-queries are preferable as they are more readable and have a better performance.
So instead of:

.. code-block:: java

    @Query("""
            SELECT COUNT (DISTINCT p) FROM StudentParticipation p
                WHERE p.exercise.id = :#{#exerciseId}
                AND EXISTS (SELECT s FROM Submission s
                    WHERE s.participation.id = p.id
                    AND s.submitted = TRUE
            """)
    long countByExerciseIdSubmitted(@Param("exerciseId") long exerciseId);


you should use:

.. code-block:: java

    @Query("""
            SELECT COUNT (DISTINCT p) FROM StudentParticipation p JOIN p.submissions s
                WHERE p.exercise.id = :#{#exerciseId}
                AND s.submitted = TRUE
            """)
    long countByExerciseIdSubmitted(@Param("exerciseId") long exerciseId);

Functionally both queries extract the same result set, but the first one is less efficient as the sub-query is calculated for each StudentParticipation.


20. REST endpoint best practices for authorization
==================================================

To prevent unauthorized access to resources Artemis employs a two-step system:

#. ``PreAuthorize`` annotations are responsible for blocking users with wrong or missing authorization roles without querying the database.
#. The ``AuthorizationCheckService`` is responsible for checking access rights to individual resources by querying the database.

Because the first method without database queries is substantially faster, always annotate your REST endpoints with ``@PreAuthorize``. Pass the role with the *least* permissions as parameter to the ``hasRole`` method.
The following example makes the call only accessible to ADMIN and INSTRUCTOR users:

.. code-block:: java

    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ProgrammingExercise> getProgrammingExercise(@PathVariable long exerciseId) {
    }

Artemis distinguishes between five different roles: ADMIN, INSTRUCTOR, TA (teaching assistant), USER and ANONYMOUS.
Each of the roles has the all the access rights of the roles following it, e.g. ANONOYMOUS has almost no rights, while ADMIN users can access every page.

If a user passess the ``PreAuthorize`` check, the access to individual resources like courses and exercises still has to be checked. (A user can be a teaching assistant in one course, but only a student in another, for example.)
However, do not fetch the user from the database yourself (unless you need to re-use the user object), but only hand a role to the ``AuthorizationCheckService``:

.. code-block:: java

        // If we pass 'null' instead of a user here, the service will fetch the user object
        // and check if the user has at least the given role and access to the resource
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, null);

To reduce duplication, do not add explicit checks for authorization or existence of an entity but always use the ``AuthorizationCheckService``:

.. code-block:: java

    @GetMapping(Endpoints.GET_FOR_COURSE)
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<List<ProgrammingExercise>> getActiveProgrammingExercisesForCourse(@PathVariable Long courseId) {
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);

        List<ProgrammingExercise> exercises = programmingExerciseService.findActiveExercisesByCourseId(courseId);
        return ResponseEntity.ok().body(exercises);
    }


The course repository call takes care of throwing a ``404 Not Found`` exception if there exists no matching course. The ``AuthorizationCheckService`` throws a ``403 Forbidden`` exception if the user with the given role is unauthorized. Afterwards delegate to a service or repository method. The code becomes much shorter, cleaner and more maintainable.


21. Assert using the most specific overload method
==================================================

When expecting results use ``assertThat`` for server tests. That call **must** be followed by another assertion statement like ``isTrue()``. It is best practice to use more specific assertion statement rather than always expecting boolean values.

For example, instead of

.. code-block:: java

    assertThat(submissionInDb.isPresent()).isTrue();
    assertThat(submissionInDb.get().getFilePath().contains("ffile.png")).isTrue();

use the methods from inside the ``assertThat`` directly:

.. code-block:: java

    assertThat(submissionInDb).isPresent();
    assertThat(submissionInDb.get().getFilePath()).contains("ffile.png");

This gives better error messages when an assertion fails and improves the code readability. However, be aware that not all methods can be used for assertions like this.

If you can't avoid using ``isTrue`` use the ``as`` keyword to add a custom error message:

.. code-block:: java

    assertThat(submission.isSubmittedInTime()).as("submission was not in time").isTrue();

Please read `the AssertJ documentation <https://assertj.github.io/doc/#assertj-core-assertions-guide>`__, especially the `section about avoiding incorrect usage <https://assertj.github.io/doc/#assertj-core-incorrect-usage>`__.


Some parts of these guidelines are adapted from https://medium.com/@madhupathy/ultimate-clean-code-guide-for-java-spring-based-applications-4d4c9095cc2a

22. General Testing Tips
========================
(Adapted from `here <https://confluence.ase.in.tum.de/display/ArTEMiS/Best+Practices+for+writing+Java+Programming+Exercise+Tests+in+Artemis>`_)

Write meaningful comments for your tests.
These comments should contain information about what is tested specifically, which task from the problem statement is addressed or which TODO (if there are numbered TODOs in the template),
how many points this test is worth when passed and more if necessary.

.. code-block:: java

    /**
     * Tests that borrow() in Book successfully sets the available attribute to false
     * Problem Statement Task 2.1
     * Worth 1.5 Points (Weight: 1)
     */
    @Test
    public void testBorrowInBook() {
        // Test Code
    }

Better yet, for manual correction, make it the display name of the test. This allows the assessors to execute the tests in the IDE, have more meaningful names displayed and count points better.

.. code-block:: java

    @DisplayName("1.5 P | Books can be borrowed successfully")
    @Test
    public void testBorrowInBook() {
        // Test Code
    }

Use appropriate and descriptive names for test cases. After exams, test names will be used to create statistics. If the tests are called test1, test2, test3, and so on, no one can read those statistics.
This is the same reason as to why you should not name your variables int a, double b, String c, and so on. For example, if you want to test the method borrow in the class Book, testBorrowInBook() would be an appropriate name for the test case.

.. code-block:: java

    @Test
    public void testBorrowInBook() {
        // Test Code
    }

Set appropriate timeouts for test cases and take Bamboo into consideration. For regular test cases, using a ``@StrictTimeout(1)`` annotation is enough. This represents a strict timeout of one second.
The value type of the strict timeout annotation defaults to seconds, if you need, for some reason, a shorter timeout, something like ``@StrictTimeout(value = 500, unit = TimeUnit.MILLISECONDS)`` works as well.

.. code-block:: java

    @Test
    @StrictTimeout(1)
    public void testBorrowInBook() {
        // Test Code
    }



Avoid assert statements and use conditional ``fail()`` calls instead to hide confusing information from the students.
This could be considered bad practice in regular conditions but helps to create fail messages that are less confusing, especially for beginners.
This also hides information about test implementation details.

.. code-block:: java

    @Test
    public void testBorrowInBook() {
        Object book = newInstance("Book", 0, "Some title");
        invokeMethod(book, "borrow");
        assertFalse((Boolean) invokeMethod(book, "isAvailable"), "A borrowed book must be unavailable!");
    }

If the student fails the test, Artemis will display something like org.opentest4j.AssertionFailedError:
A borrowed book must be unavailable! ==> Expected <false> but was <true>. The part after '==>' should not be shown to the student as it contains implementation details.

.. code-block:: java

    @Test
    public void testBorrowInBook() {
        Object book = newInstance("Book", 0, "Some title");
        invokeMethod(book, "borrow");
        if ((Boolean) invokeMethod(book, "isAvailable")) {
            fail("A borrowed book is not available anymore!");
        }
    }

This will just display the message 'org.opentest4j.AssertionFailedError: A borrowed book is not available anymore!', which focuses, except for the first part, on the actual error instead of test internals.



Students can break everything and will break everything. It is important to write tests that are as independent of the student's code as possible. Avoid direct code references and use reflective operations instead.
That way if a student modifies the template by accident and the test code would normally not compile, they still get more meaningful feedback than a simple build error.

.. code-block:: java

    @Test
    public void testBorrowInBook() {
        Book book = new Book(0, "Some title");
        book.borrow();
        if (book.isAvailable()) {
            fail("A borrowed book must be unavailable!");
        }
    }

This will lead to a build error if the student accidentally messes with the Book class. Build errors usually have a cryptic fail message on Artemis and it should be avoided that students see those confusing error messages.

.. code-block:: java

    @Test
    public void testBorrowInBook() {
        Object book = newInstance("Book", 0, "Some title");
        invokeMethod(book, "borrow");
        if ((Boolean) invokeMethod(book, "isAvailable")) {
            fail("A borrowed book must be unavailable!");
        }
    }

This will lead to an error message like 'The class 'Book' was not found within the submission. Make sure to implement it properly.' Which is clear and tells the student exactly what is wrong with their code.



Some courses use long package identifiers like 'de.tum.in.ase.pse'. When instantiating objects with reflections,
the instantiation method usually takes the full canonical name of a class, which is 'de.tum.in.ase.pse.Book' for example.
To avoid writing out this full canonical name all the time, you can add a constant String attribute representing the base package name at the top of your test class

.. code-block:: java

    private static final String BASE_PACKAGE = "de.tum.in.ase.pse.";

    @Test
    public void testBorrowInBook() {
        Object book = newInstance(BASE_PACKAGE + "Book", 0, "Some title");
        // Test Code
    }

It is possible that students hardcode values to pass a certain set of tests, therefore you should check whether this is the case or not.
This is especially important in an exam setting, so students don't get awarded points for a solution that does not fulfill the requirements described in the problem statement.

Avoid relying on a specific order in which students solve the tasks.
Tests should successfully cover one aspect of the submission without requiring the implementation of a different part of the system even if those aspects are heavily interlinked.
Example from PSE2021 Broker Pattern: The student is supposed to expand the translate method first and after that the runService method in the following setting:

.. code-block:: java

    public String translate(String word, String language) {
        return switch (language) {
            case TranslationService.LANGUAGE_GERMAN -> translateToGerman(word);
            // TODO: Add a case for the French language
            default -> throw new IllegalStateException("Illegal language requested: " + language);
        };
    }

    public String runService(String serviceName, String parameter) {
        String result = null;
        if (serviceName.equals(TranslationService.SERVICE_NAME_TRANSLATION_GERMAN)) {
            result = translate(parameter, TranslationService.LANGUAGE_GERMAN);
        }
        // TODO: Add a case for the French language
        else {
            System.out.println("Can't offer service " + serviceName);
        }
        return result;
    }

There are two separate tests, one testing the translation and the other one testing the runService method.
The test for runService must not assume that the translate method is already implemented correctly. A possible solution for this problem could look like this:

.. code-block:: java

    @Test
    public void testRunServiceInTranslationServer() {
        TranslationServer testTranslationServer = new TranslationServer() {
            public String translate(String word, String language) {
                return word + ":" + language;
            }
        }
        String expected = "Dog:French";
        String actual = testTranslationServer.runService("Dog", "French");
        if(!expected.equals(actual)) fail("Descriptive fail message");
    }

This test correctly tests if the student added the case for the French language and called the appropriate method with the appropriate parameters. Because the translation method was overridden,
it doesn't matter whether the student has already completed the previous task or not.
(If you do that, you should have some way to deal with students that make the class or method final, either via problem statement or test, otherwise students get compilation errors in the test code)


Make use of JUnit 5 Features:
https://junit.org/junit5/docs/current/user-guide/#writing-tests
https://junit.org/junit5/docs/current/api/org.junit.jupiter.api/org/junit/jupiter/api/Assertions.html

General Testing best practices are found on https://phauer.com/2019/modern-best-practices-testing-java/

Also check out https://www.baeldung.com/spring-tests

And https://howtodoinjava.com/best-practices/unit-testing-best-practices-junit-reference-guide/


23. Avoid using @MockBean
=========================

Do not use the ``@SpyBean`` or ``@MockBean`` annotation unless absolutely necessary, or possibly in an abstract Superclass. If you want to see more in detail why, take a look `here <https://www.baeldung.com/spring-tests>`__.
Basically every time ``@MockBean`` appears in a class, the ApplicationContext cache gets marked as dirty, hence the runner will clean the cache after the test-class is done and restarts the application context.
This leads to a large overhead, which makes the tests tend to take a lot more time.

Here is an example how to replace a ``@SpyBean``. We wanted to test a catch case which is only executed if an IOException gets thrown. We did this by mocking the service method and making it throw an Exception.

.. code-block:: java

    public class TestExport extends AbstractSpringIntegrationBambooBitbucketJiraTest {
        @SpyBean
        private FileUploadSubmissionExportService fileUploadSubmissionExportService;

        @Test
        @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
        public void testExportAll_IOException() throws Exception {
            doThrow(IOException.class).when(fileUploadSubmissionExportService).export(any(), any());
            request.postWithResponseBodyFile("/api/file-upload-export/" + fileUploadExercise.getId()", HttpStatus.BAD_REQUEST);
        }
    }

This, as mentioned above, we should really avoid.
Instead we can use `Static Mocks <https://asolntsev.github.io/en/2020/07/11/mockito-static-methods/>`_. When we look deeper in the ``export()`` method we find that there is a call of ``File.newOutputStream(..)``.
Now, instead of mocking the whole Service we can just mock this static method, like this:

.. code-block:: java

    public class TestExport extends AbstractSpringIntegrationBambooBitbucketJiraTest {
        // No beans used anymore
        @Test
        @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
        public void testExportAll_IOException() throws Exception {
            MockedStatic<Files> mockedFiles = mockStatic(Files.class);
            mockedFiles.when(() -> Files.newOutputStream(any(), any())).thenThrow(IOException.class);
            request.postWithResponseBodyFile("/api/file-upload-export/" + fileUploadExercise.getId()", HttpStatus.BAD_REQUEST);

            mockedFiles.close();
        }
    }

What you notice here is that we can avoid the use of a Bean and also test deeper. Instead of mocking the uppermost method we will only throw the exception at the place where it could actually happen. Very important to mention is that you need to close the mock at the end of the test again.

For a real example where a SpyBean was replaced with a static mock look at the SubmissionExportIntegrationTest.java in `here <https://github.com/ls1intum/Artemis/commit/4843137aa01cfdf27ea019400c48df00df36ed45>`__.
