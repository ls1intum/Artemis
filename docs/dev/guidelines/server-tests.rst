************
Server Tests
************

0. Assert using the most specific overload method
==================================================

When expecting results use ``assertThat`` from the AssertJ library (add link) for server tests. That call **must** be followed by another assertion statement like ``isTrue()``. It is best practice to use more specific assertion statement rather than always expecting boolean values.

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

1. General Testing Tips
========================

Write meaningful comments for your tests.
These comments should contain information about what is tested specifically.

.. code-block:: java

    /**
     * Tests that borrow() in Book successfully sets the available attribute to false
     */
    @Test
    void testBorrowInBook() {
        // Test Code
    }

Use appropriate and descriptive names for test cases. This makes it easier for other developers to understand what you actually test without looking deeper into it.
This is the same reason why you should not name your variables int a, double b, String c, and so on. For example, if you want to test the method borrow in the class Book, testBorrowInBook() would be an appropriate name for the test case.

.. code-block:: java

    @Test
    void testBorrowInBook() {
        // Test Code
    }

Try to follow the best practices for Java testing:

* Write small and specific tests by heavily using helper functions, parameterized tests, AssertJ’s powerful assertions, not overusing variables, asserting only what’s relevant and avoiding one test for all corner cases.
* Write self-contained tests by revealing all relevant parameters, insert data right in the test and prefer composition over inheritance.
* Write dumb tests by avoiding the reuse of production code and focusing on comparing output values with hard-coded values.
* KISS > DRY ("Keep it simple, Stupid!" and "Don't repeat yourself!")
* Invest in a testable implementation by avoiding static access, using constructor injection, using Clocks and separating business logic from asynchronous execution.

For a more detailed overview definitely check out: https://phauer.com/2019/modern-best-practices-testing-java/


Make use of JUnit 5 Features:
https://junit.org/junit5/docs/current/user-guide/#writing-tests
https://junit.org/junit5/docs/current/api/org.junit.jupiter.api/org/junit/jupiter/api/Assertions.html

Here you can find JUnit 5 best practices:
https://howtodoinjava.com/best-practices/unit-testing-best-practices-junit-reference-guide/

Also check out this page for spring related testing:
https://www.baeldung.com/spring-tests

If you want to write tests for Programming Exercises to test student's submissions check out `this <https://confluence.ase.in.tum.de/display/ArTEMiS/Best+Practices+for+writing+Java+Programming+Exercise+Tests+in+Artemis>`__.

2. Counting database query calls within tests
==============================================

It's possible to write tests that check how many database calls are performed during a REST call. This is useful to ensure that code changes don't lead to more database calls,
or at least to remind developers in case they do. It's especially important for commonly used endpoints that users access multiple times or every time they use Artemis.
However, we should consider carefully before adding such assertions to a test as it makes the test more tedious to maintain.

An example on how to track how many database calls are performed during a REST call is shown below. It uses the ``HibernateQueryInterceptor`` which counts the number of queries.
For ease of use, a custom assert ``assertThatDb`` was added that allows to do the check in one line. It also returns the original result of the REST call and so allows you to
add any other assertions to the test, as shown below.

.. code-block:: java

    class TestClass {

        @Test
        @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
        void testQueryCount() throws Exception {
            Course course = assertThatDb(() -> request.get("/api/courses/" + courses.get(0).getId() + "/for-dashboard", HttpStatus.OK, Course.class)).hasBeenCalledTimes(3);
            assertThat(course).isNotNull();
        }
    }

3. Avoid using @MockBean
=========================

Do not use the ``@SpyBean`` or ``@MockBean`` annotation unless absolutely necessary, or possibly in an abstract Superclass. If you want to see why in more detail, take a look `here <https://www.baeldung.com/spring-tests>`__.
Basically, every time ``@MockBean`` appears in a class, the ApplicationContext cache gets marked as dirty, hence the runner will clean the cache after the test-class is done and restarts the application context.
This leads to a large overhead, which tends to make the tests take a lot more time.

Here is an example how to replace a ``@SpyBean``. We wanted to test an edge case which is only executed if an ``IOException`` is thrown. We did this by mocking the service method and making it throw an Exception.

.. code-block:: java

    class TestExport extends AbstractSpringIntegrationBambooBitbucketJiraTest {
        @SpyBean
        private FileUploadSubmissionExportService fileUploadSubmissionExportService;

        @Test
        @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
        void testExportAll_IOException() throws Exception {
            doThrow(IOException.class).when(fileUploadSubmissionExportService).export(any(), any());
            request.postWithResponseBodyFile("/api/file-upload-export/" + fileUploadExercise.getId(), HttpStatus.BAD_REQUEST);
        }
    }

As mentioned above, we should really avoid this.
Instead we can use `Static Mocks <https://asolntsev.github.io/en/2020/07/11/mockito-static-methods/>`_. When we look deeper in the ``export()`` method we find that there is a call of ``File.newOutputStream(..)``.
Now, instead of mocking the whole Service, we can just mock the static method, like this:

.. code-block:: java

    class TestExport extends AbstractSpringIntegrationBambooBitbucketJiraTest {
        // No beans used anymore
        @Test
        @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
        void testExportAll_IOException() throws Exception {
            MockedStatic<Files> mockedFiles = mockStatic(Files.class);
            mockedFiles.when(() -> Files.newOutputStream(any(), any())).thenThrow(IOException.class);
            request.postWithResponseBodyFile("/api/file-upload-export/" + fileUploadExercise.getId(), HttpStatus.BAD_REQUEST);

            mockedFiles.close();
        }
    }

You should notice here that we can avoid the use of a Bean and also test deeper. Instead of mocking the uppermost method we only throw the exception at the place where it could actually happen. Very important to mention is that you need to close the mock at the end of the test again.

For a real example where a SpyBean was replaced with a static mock look at the SubmissionExportIntegrationTest.java in `here <https://github.com/ls1intum/Artemis/commit/4843137aa01cfdf27ea019400c48df00df36ed45>`__.

3. UtilServices and Factories
=========================
