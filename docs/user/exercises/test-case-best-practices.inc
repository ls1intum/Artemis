General best practices
^^^^^^^^^^^^^^^^^^^^^^

- **Write Meaningful Comments for your Tests**

  These comments should contain information about what is tested specifically, which task from the problem statement
  is addressed or which TODO (if there are numbered TODOs in the template), how many points this test is worth when
  passed and more if necessary. But make sure to keep the information consistent with the settings on Artemis like the weight of each testcase.

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

  Better yet, for manual correction, use these comments in the display name of the test. This allows the assessors, who execute the
  tests in the IDE, to have more meaningful names displayed. The following example would make counting points easier.

  .. code-block:: java

    @DisplayName("1.5 P | Books can be borrowed successfully")
    @Test
    public void testBorrowInBook() {
        // Test Code
    }

- **Use Appropriate and Descriptive Names for Test Cases**

  After exercises and exams, test names will be used to create statistics. If the tests are called *test1*, *test2*, *test3*,
  it will be hard to read those statistics. This is the same reason as to why you should not name your variables ``int a``,
  ``double b``, ``String c``. For example, if you want to test the method ``borrow`` in the class ``Book``,
  ``testBorrowInBook()`` would be an appropriate name for the test case.

  .. code-block:: java

    @Test
    public void testBorrowInBook() {
        // Test Code
    }

  If you have many tests in different (nested) classes that are not completely distinct, add the name of the tested
  class to the test to avoid having two tests with the same names. For example, if you test both ``add`` methods of a
  LinkedList and an ArrayList. Having the same test name will lead to errors on Artemis.
  Clearer test names also makes it easier to read and configure the grading in Artemis.

  .. code-block:: java

    @Test
    public void test_LinkedList_add() {
        // Test Code
    }

  .. hint::
    For Java exercises: If all test methods are in a single class this is not necessary, because the Java compiler
    won't allow multiple methods with override-equivalent signatures.

- **Use Appropriate Timeouts for Test Cases**

  For regular test cases, using a ``@StrictTimeout(1)``  annotation is enough. This represents a strict timeout of one
  second. The value type of the strict timeout annotation defaults to seconds. If you need a shorter timeout, you can use
  ``@StrictTimeout(value = 500, unit = TimeUnit.MILLISECONDS)``. This annotation can also be added over a test class.
  In that case the timeout applies individually to every test in that class.

  .. code-block:: java

    @Test
    @StrictTimeout(1)
    public void testBorrowInBook() {
        // Test Code
    }

  .. note::
      When defining timeouts, you should take into account that the tests are run on a Continuous Integration Server
      (using build agents). The tests will most likely execute a lot faster on your local machine.

- **Avoiding Assert Statements**

  Instead use conditional ``fail()`` calls to hide confusing information from the students. This could be considered a
  bad practice in regular testing but it helps to create fail messages that are less confusing, especially for beginners.
  Additionally, this also hides information about test implementation details, if the specific inputs or expected outputs
  should stay unknown to students.

  .. code-block:: java

    @Test
    public void testBorrowInBook() {
        Object book = newInstance("Book", 0, "Some title");
        invokeMethod(book, "borrow");
        assertFalse((Boolean) invokeMethod(book, "isAvailable"), "A borrowed book must be unavailable!");
    }

  If the student fails the test, Artemis will display something like *org.opentest4j.AssertionFailedError: A borrowed book must be unavailable! ==> Expected <false> but was <true>*.
  The part after '==>' should not be shown to the student as it contains implementation detail.

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

- **Write Tests that are as Independent of the Student's Code as Possible**

  Students can break everything and will break everything. Avoid direct code references and use reflective operations
  instead. That way if a student modifies the template by accident and the test code would normally not compile,
  they still get more meaningful feedback than a simple build error.

  .. code-block:: java

    @Test
    public void testBorrowInBook() {
        Book book = new Book(0, "Some title");
        book.borrow();
        if (book.isAvailable()) {
            fail("A borrowed book must be unavailable!");
        }
    }

  The code above will lead to a build error, if the student accidentally changes the ``Book`` class. Test code build errors
  usually have a cryptic fail messages and it should be avoided that students see those confusing error messages.

  .. code-block:: java

    @Test
    public void testBorrowInBook() {
        Object book = newInstance("Book", 0, "Some title");
        invokeMethod(book, "borrow");
        if ((Boolean) invokeMethod(book, "isAvailable")) {
            fail("A borrowed book must be unavailable!");
        }
    }

  The code above will lead to an error message like *The class 'Book' was not found within the submission. Make sure to implement it properly.*
  The message is clear and tells the student exactly what is wrong with their code.

- **Check for Hard-Coded Student Solutions**

  It is possible that students hardcode values to pass a certain set of tests. Therefore, you should check whether this
  is the case or not. This is especially important in an exam setting, so students don't get awarded points for a
  solution that does not fulfill the requirements described in the problem statement.

- **Avoid Relying on a Specific Order in which Students Solve the Tasks**

  Tests should successfully cover one aspect of the submission without requiring the implementation of a different part
  of the exercise, even if those aspects are heavily coupled.

  In this example, the student is supposed to expand the ``translate`` method first and after that the
  ``runService`` method:

  .. code-block:: java

    public String translate(String word, String language) {
        return switch (language) {
            case TranslationService.LANGUAGE_GERMAN -> translateToGerman(word);
            // TODO: Add a case for the French language
            default -> throw new IllegalStateException("Illegal language requested: " + language);
        };
    }

  .. code-block:: java

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

  There are two separate tests, one testing the ``translation`` and the other one testing the ``runService`` method.
  The test for ``runService`` must not assume that the ``translate`` method is already implemented correctly.
  A possible solution for this problem could look like this:

  .. code-block:: java

    @Test
    public void testRunServiceInTranslationServer() {
        TranslationServer testTranslationServer = new TranslationServer() {
            public String translate(String word, String language) {
                return word + ":" + language;
            }
        }
        String expected = "Dog:French";
        String actual = testTranslationServer.runService("French", "Dog");
        if(!expected.equals(actual)) fail("Descriptive fail message");
    }

  This test correctly checks, if the student added the case for the French language and called the appropriate method
  with the appropriate parameters. Because the translation method was overridden, it doesn't matter whether the student
  has already completed the previous task or not.

  .. note::
    If you use the this technique, you should have some way to deal with students that make the class or method final, either via
    problem statement or test. Otherwise students get compilation errors in the test code.

- **Catch Possible Student Errors**

  Handle possible student mistakes appropriately in the test case. For example, if a method of the student returns null
  and this is not handled appropriately in the test, this might produce a ``NullPointerException``, which will lead to a
  cryptic fail message. A null check in the test case allows providing a more clear fail message to the student.

Java best practices
^^^^^^^^^^^^^^^^^^^

- **Use Constant String Attributes to Represent the Base Package**

  Some courses use long package identifiers like ``de.tum.in.ase.pse``. When instantiating objects with reflections,
  the instantiation method usually takes the full canonical name of a class, which is ``de.tum.in.ase.pse.Book`` for example.
  To avoid writing out this full canonical name all the time, you can add a constant String attribute representing the
  base package name to the top of your test class.

  .. code-block:: java

    private static final String BASE_PACKAGE = "de.tum.in.ase.pse.";

    @Test
    public void testBorrowInBook() {
      Object book = newInstance(BASE_PACKAGE + "Book", 0, "Some title");
      // Test Code
    }

- **Use JUnit5 and Ares Features**

  More information can be found in the `JUnit5 <https://junit.org/junit5/docs/current/user-guide/#writing-tests>`__ and
  `Ares <https://github.com/ls1intum/Ares#readme>`__ documentation. The following list adds some
  useful notes:

  - Use `Nested Tests <https://junit.org/junit5/docs/current/user-guide/#writing-tests-nested>`__ to group tests

    In combination with display names for both tests and the nested classes, this allows to structure grading with
    tests and the grouping is also helpful when executing the test in the IDE. One example would be to structure the
    tests by exercise sub-tasks or to group tests that all check a single more complicated method. You can also do this
    by using static nested classes instead of inner classes annotated with ``@Nested`` (decide depending on your scenario).

  - Define a custom, well-structured and predictable test execution order with ``@Order``
  - If you test multiple cases in a single test (e.g. because you want "all or nothing" grading for those cases or simply
    check with multiple inputs), you can use `assertDoesNotThrow <https://junit.org/junit5/docs/current/api/org.junit.jupiter.api/org/junit/jupiter/api/Assertions.html#assertDoesNotThrow(org.junit.jupiter.api.function.Executable)>`__
    to pass a message that is displayed in Artemis, in case an exception occurs in the student code.
  - If you want to test multiple assertions that are fairly independent in a single test (e.g. because you want
    "all or nothing" grading for those cases or simply check with multiple inputs) you should consider if
    `assertAll <https://junit.org/junit5/docs/current/api/org.junit.jupiter.api/org/junit/jupiter/api/Assertions.html#assertAll(java.lang.String,org.junit.jupiter.api.function.Executable...)>`__
    is what you need. This will execute all passed executables and aggregate the failures, which allows showing students
    multiple wrong aspects of their solution directly.
  - If you have special needs, consider using `Dynamic Tests <https://junit.org/junit5/docs/current/user-guide/#writing-tests-dynamic-tests>`__
    and/or write your own `extension <https://junit.org/junit5/docs/current/user-guide/#extensions>`__.
  - If you need to test tests, use the `JUnit Platform Test Kit <https://junit.org/junit5/docs/current/user-guide/#testkit>`__.
    For providing wrong implementations that students need to test, consider

    - passing interface implementations (easiest and safe) → pure Java
    - pass mocked objects (flexible and safe, students don't need to know) → EasyMock/Mockito
    - mock single methods of tested objects (partial mock) or mock constructors → one of the above plus PowerMock

- **Define your own Annotations**

  Own annotations are an easy and powerful tool that keeps your code readable. This example defines an annotation
  that combines both test and strict timeout.

  .. code-block:: java

    @Test
    @StrictTimeout(10)
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD})
    public @interface LongTest {

    }

- **Consider using jqwik for Property-Based Testing**

  `Jqwik <https://jqwik.net>`__ allows testing with arbitrary inputs and shrinks the test errors, resulting in excellent
  counter examples when student code fails (usually exactly the edge case).

- **Eclipse Compiler and Best-Effort Compilation**

  Use the Eclipse Java Compiler for partial, best-effort compilation. This is particularly useful for exam exercises and
  other more "dirty" programming work. It is also useful for testing complicated generics (you really don't want to do
  that with java reflection). This causes compilation errors to be transformed into errors that are thrown where code
  does not compile. This is done on method and class level (essentially replacing the body of the class/method with
  ``throw new Error("Unresolved compilation problems: ...")``, which is then thrown whenever the class/method is used).

  If you intend to write tests that exploit that, make sure that only the body of the test methods does not compile
  (e.g. if a student didn't implement something from a task). If your complete test class does not compile, this causes
  the complete test class initialization to fail, which results in cryptic feedback in Artemis. Anything in the test class
  that is not a method body or nested class body must compile. This includes method return types and parameter types,
  and therefore also lambdas! You can avoid that by using e.g. the ``Object`` class and cast inside / at the call site.
  Use e.g. a nested class for Fields, Field types and Methods with student class return/parameter types that could
  potentially not compile. Because the nested class is a separate class which is loaded separately, the top level test
  class will still load successfully and only methods using that nested class will fail due to the error from the nested
  class initialization.

  You can choose to use the Eclipse compiler for both student and test code, or for test code only, depending on whether
  you want to grade not fully compiling code.

  .. note::
    The Eclipse Compiler released under this maven coordinate does not always support the latest Java version. You can
    still compile student code with the latest Java version and only the test code with the previous.

  - **Variant: Test Code Only**

    .. raw:: html

       <details>
       <summary><a>Test Code Only</a></summary>

    .. code-block:: xml

       <plugin>
           <groupId>org.apache.maven.plugins</groupId>
           <artifactId>maven-compiler-plugin</artifactId>
           <version>3.8.1</version>
           <configuration>
               <source>17</source>
               <target>17</target>
           </configuration>
           <executions>
               <execution>
                   <id>default-testCompile</id>
                   <phase>process-test-sources</phase>
                   <goals>
                       <goal>testCompile</goal>
                   </goals>
                   <configuration>
                       <source>17</source>
                       <target>17</target>
                       <compilerId>jdt</compilerId>
                       <optimize>true</optimize>
                       <failOnError>false</failOnError>
                       <compilerArguments>
                           <proceedOnError />
                       </compilerArguments>
                   </configuration>
               </execution>
           </executions>
           <dependencies>
               <dependency>
                   <groupId>org.eclipse.tycho</groupId>
                   <artifactId>tycho-compiler-jdt</artifactId>
                   <version>2.1.0</version>
               </dependency>
           </dependencies>
       </plugin>

    .. raw:: html

       </details>

  - **Variant Student and Test Code:**

    .. raw:: html

       <details>
       <summary><a>Variant Student and Test Code</a></summary>

    .. code-block:: xml

       <plugin>
           <groupId>org.apache.maven.plugins</groupId>
           <artifactId>maven-compiler-plugin</artifactId>
           <version>3.8.1</version>
           <configuration>
               <source>17</source>
               <target>17</target>
               <compilerId>jdt</compilerId>
               <optimize>true</optimize>
               <failOnError>false</failOnError>
               <compilerArguments>
                   <proceedOnError />
               </compilerArguments>
           </configuration>
           <dependencies>
               <dependency>
                   <groupId>org.eclipse.tycho</groupId>
                   <artifactId>tycho-compiler-jdt</artifactId>
                   <version>2.1.0</version>
               </dependency>
           </dependencies>
       </plugin>

    .. raw:: html

       </details>

- **Common Pitfalls / Problems:**

  - The Reflection API is limited when it comes to constant attributes. Constant attributes are static final attributes
    with a primitive or ``String`` type. Java inlines such attributes during compile-time, which makes it more or less
    impossible to change the value during runtime.
  - Be careful with long output, arrays or ``Strings``. This might be unreadable in Artemis or even cut of after 5000 characters.
