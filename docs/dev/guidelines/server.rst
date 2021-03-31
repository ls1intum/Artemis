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

Some parts of these guidelines are adapted from https://medium.com/@madhupathy/ultimate-clean-code-guide-for-java-spring-based-applications-4d4c9095cc2a
