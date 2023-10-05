************
Server Tests
************

0. Assert using the most specific overload method
==================================================

When asserting in server tests, use ``assertThat`` from the `AssertJ <https://github.com/assertj/assertj>`__ library. Another assertion statement, such as ``isEqualTo()``, **must**  follow the call. Using specific assertion statements rather than always expecting boolean values is best practice.

For example, instead of

.. code-block:: java

    assertThat(submissionInDb.isPresent()).isTrue();
    assertThat(submissionInDb.get().getFilePath().contains("ffile.png")).isTrue();

use the built-in assertions directly:

.. code-block:: java

    assertThat(submissionInDb).isPresent();
    assertThat(submissionInDb.get().getFilePath()).contains("ffile.png");

These assertions provide better error messages when they fail and improve the code readability. However, not all methods are suitable for this type of assertion.
If the ``isTrue`` assertion is unavoidable, specify a custom error message using the ``as`` keyword:

.. code-block:: java

    assertThat(submission.isSubmittedInTime()).as("submission was not in time").isTrue();

For more information, please read `the AssertJ documentation <https://assertj.github.io/doc/#assertj-core-assertions-guide>`__, especially the `section about avoiding incorrect usage <https://assertj.github.io/doc/#assertj-core-incorrect-usage>`__.

1. General Testing Tips
========================

Write meaningful comments for your tests. These comments should contain information about what is tested specifically.

.. code-block:: java

    /**
     * Tests that borrow() in Book successfully sets the available attribute to false
     */
    @Test
    void testBorrowInBook() {
        // Test Code
    }

Use appropriate and descriptive names for test cases. This makes it easier for other developers to understand what you actually test without looking deeper into it.
For the same reason you should not name your variables ``int a``, ``double b``, ``String c``, and so on. Instead, you should name the variables using ``actual`` and ``expected`` prefix.

For example, if you want to test the method borrow in the class Book, ``testBorrowInBook()`` would be an appropriate name for the test case.

.. code-block:: java

    @Test
    void testBorrowInBook() {
        // Test Code
    }

Try to follow best practices for Java testing:

* Write small and specific tests by heavily using helper functions with relevant parameters.
* Assert only what’s relevant and avoid writing one single test covering all edge cases.
* Write dumb tests by avoiding the reuse of production code and focusing on comparing output values with hard-coded values.
* Invest in a testable implementation by avoiding static access, using constructor injection, and separating business logic from asynchronous execution.
* Instead of using random, use fixed test data, making the test logs easier to understand and when an assertion fails.
* Make use of `JUnit 5 <https://junit.org/junit5/docs/current/user-guide/#writing-tests>`__ features such as parameterized tests.
* Follow `best practices <https://www.baeldung.com/spring-tests>`__ related to spring testing.

For a more detailed overview check out `modern best testing practices <https://phauer.com/2019/modern-best-practices-testing-java/>`__.

If you want to write tests for Programming Exercises to test student's submissions check out `this <https://confluence.ase.in.tum.de/display/ArTEMiS/Best+Practices+for+writing+Java+Programming+Exercise+Tests+in+Artemis>`__.

2. Counting database query calls within tests
==============================================

It's possible to write tests checking how many database calls are performed during a REST call. This is useful to ensure that code changes don't lead to more database calls,
or to at least remind developers in case they do. This is especially important for commonly used endpoints.
However, we should carefully consider adding such assertions to a test as it makes the test more tedious to maintain.

An example on how to track how many database calls are performed during a REST call is shown below. It uses the ``HibernateQueryInterceptor`` which counts the number of queries.
The custom assert ``assertThatDb`` allows you to check the number of database calls in one line. It also returns the original result of the REST call and so allows you to
add any other assertions to the test, as shown below.how many

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
Every time ``@MockBean`` appears in a class, the application context cache gets marked as dirty, meaning the runner will clean the cache after the test-class is done executing. The application context is restarted, which leads to a large overhead of an additional server start.

Here is an example of how to replace a ``@SpyBean``. We wanted to test an edge case which is only executed if an ``IOException`` is thrown. We did this by mocking the service method and making it throw an Exception.

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

To avoid the use of a new SpyBean, we now use `Static Mocks <https://asolntsev.github.io/en/2020/07/11/mockito-static-methods/>`__. When taking a closer look into the ``export()`` method we find that there is a call of ``File.newOutputStream(..)``.
Now, instead of mocking the whole service, we can just mock the static method:

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

We no longer mock the uppermost method but only throw the exception at the place where it could actually happen. At the end of the test you **need to close** the mock again.


For a real example where a SpyBean was replaced with a static mock look at the ``SubmissionExportIntegrationTest.java`` `here <https://github.com/ls1intum/Artemis/commit/4843137aa01cfdf27ea019400c48df00df36ed45>`__.

3. UtilServices and Factories
=============================
