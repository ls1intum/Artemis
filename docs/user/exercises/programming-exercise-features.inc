.. _programming_features:

Feature Overview
^^^^^^^^^^^^^^^^

Artemis and its version control and continuous integration infrastructure is independent of the programming language and thus supports
teaching and learning with any programming language that can be compiled and tested on the command line.
Instructors have a lot of freedom in defining the environment (e.g. using build agents and Docker images) in which student code is executed and tested.
To simplify the setup of programming exercises, Artemis supports several ``templates`` that show how the setup works.
Instructors can still use those templates to generate programming exercises and then adapt and customize the settings in the repositories and build plans.


- The support for a specific programming language ``templates`` depends on the used ``continuous integration`` system. The table below gives an overview:

  +----------------------+----------+---------+
  | Programming Language | Local CI | Jenkins |
  +======================+==========+=========+
  | Java                 | yes      | yes     |
  +----------------------+----------+---------+
  | Python               | yes      | yes     |
  +----------------------+----------+---------+
  | C                    | yes      | yes     |
  +----------------------+----------+---------+
  | Haskell              | yes      | yes     |
  +----------------------+----------+---------+
  | Kotlin               | yes      | yes     |
  +----------------------+----------+---------+
  | VHDL                 | yes      | no      |
  +----------------------+----------+---------+
  | Assembler            | yes      | no      |
  +----------------------+----------+---------+
  | Swift                | yes      | yes     |
  +----------------------+----------+---------+
  | OCaml                | yes      | no      |
  +----------------------+----------+---------+
  | Rust                 | yes      | yes     |
  +----------------------+----------+---------+
  | JavaScript           | yes      | yes     |
  +----------------------+----------+---------+

- Not all ``templates`` support the same feature set and supported features can also change depending on the continuous integration system setup.
  Depending on the feature set, some options might not be available during the creation of the programming exercise.
  The table below provides an overview of the supported features.
  In case a feature has different support for different continuous integration systems, the table shows the differences between Local CI **(L)** and Jenkins **(J)**.
  (Note that Gitlab CI is experimental and therefore has a limited set of features which are not mentioned here to keep the overview simpler.)

  +----------------------+----------------------+----------------------+---------------------+--------------+------------------------------------------+------------------------------+----------------------------+------------------------+
  | Programming Language | Sequential Test Runs | Static Code Analysis | Plagiarism Check    | Package Name | Project Type                             | Solution Repository Checkout | Testwise Coverage Analysis | Auxiliary repositories |
  +======================+======================+======================+=====================+==============+==========================================+==============================+============================+========================+
  | Java                 | yes                  | yes                  | yes                 | yes          | Gradle, Maven, J: `DejaGnu`_             | no                           | J: yes; L: no              | L: yes, J: no          |
  +----------------------+----------------------+----------------------+---------------------+--------------+------------------------------------------+------------------------------+----------------------------+------------------------+
  | Python               | L: yes; J: no        | no                   | yes                 | no           | n/a                                      | no                           | no                         | L: yes, J: no          |
  +----------------------+----------------------+----------------------+---------------------+--------------+------------------------------------------+------------------------------+----------------------------+------------------------+
  | C                    | no                   | L: yes; J: no        | yes                 | no           | FACT, GCC                                | no                           | no                         | L: yes, J: no          |
  +----------------------+----------------------+----------------------+---------------------+--------------+------------------------------------------+------------------------------+----------------------------+------------------------+
  | C (FACT framework)   | no                   | L: yes; J: no        | yes                 | no           | n/a                                      | no                           | no                         | L: yes, J: no          |
  +----------------------+----------------------+----------------------+---------------------+--------------+------------------------------------------+------------------------------+----------------------------+------------------------+
  | Haskell              | L: yes; J: no        | no                   | no                  | no           | n/a                                      | L: yes; J: no                | no                         | L: yes, J: no          |
  +----------------------+----------------------+----------------------+---------------------+--------------+------------------------------------------+------------------------------+----------------------------+------------------------+
  | Kotlin               | yes                  | no                   | yes                 | yes          | n/a                                      | no                           | J: yes; L: no              | L: yes, J: no          |
  +----------------------+----------------------+----------------------+---------------------+--------------+------------------------------------------+------------------------------+----------------------------+------------------------+
  | VHDL                 | no                   | no                   | no                  | no           | n/a                                      | no                           | no                         | L: yes, J: no          |
  +----------------------+----------------------+----------------------+---------------------+--------------+------------------------------------------+------------------------------+----------------------------+------------------------+
  | Assembler            | no                   | no                   | no                  | no           | n/a                                      | no                           | no                         | L: yes, J: no          |
  +----------------------+----------------------+----------------------+---------------------+--------------+------------------------------------------+------------------------------+----------------------------+------------------------+
  | Swift                | no                   | yes                  | yes                 | yes          | Plain (Xcode: not supported)             | no                           | no                         | L: yes, J: no          |
  +----------------------+----------------------+----------------------+---------------------+--------------+------------------------------------------+------------------------------+----------------------------+------------------------+
  | OCaml                | no                   | no                   | no                  | no           | n/a                                      | yes                          | no                         | L: yes, J: no          |
  +----------------------+----------------------+----------------------+---------------------+--------------+------------------------------------------+------------------------------+----------------------------+------------------------+
  | Rust                 | no                   | no                   | no                  | no           | n/a                                      | no                           | no                         | L: yes, J: no          |
  +----------------------+----------------------+----------------------+---------------------+--------------+------------------------------------------+------------------------------+----------------------------+------------------------+
  | JavaScript           | no                   | no                   | no                  | no           | n/a                                      | no                           | no                         | L: yes, J: no          |
  +----------------------+----------------------+----------------------+---------------------+--------------+------------------------------------------+------------------------------+----------------------------+------------------------+

  - *Sequential Test Runs*: ``Artemis`` can generate a build plan which first executes structural and then behavioral tests. This feature can help students to better concentrate on the immediate challenge at hand.
  - *Static Code Analysis*: ``Artemis`` can generate a build plan which additionally executes static code analysis tools.
    ``Artemis`` categorizes the found issues and provides them as feedback for the students. This feature makes students aware of code quality issues in their submissions.
  - *Plagiarism Checks*: ``Artemis`` is able to automatically calculate the similarity between student submissions. A side-by-side view of similar submissions is available to confirm the plagiarism suspicion.
  - *Package Name*: A package name has to be provided
  - *Solution Repository Checkout*: Instructors are able to compare a student submission against a sample solution in the solution repository
  - *Testwise Coverage Analysis*: ``Artemis`` can generate a build plan which additionally executes a testwise coverage analysis.
    ``Artemis`` aggregates the recorded data into different metrics. This feature allows instructors to check which code in the solution submission is how often executed by the test cases.

.. note::
  Only some ``templates`` for ``LocalCI`` support ``Sequential Test Runs`` at the moment.

.. note::
  Static Code Analysis for ``C`` exercises is only supported for ``LocalCI`` at the moment.

.. note::
  Testwise Coverage Analysis is currently not supported, but will be again supported in future versions.

.. note::
  Instructors are still able to extend the generated programming exercises with additional features that are not available in one specific template.

.. note::
  Documentation for FACT framework can be found here: https://fact.readthedocs.io/en/latest/

We encourage instructors to contribute improvements to the existing ``templates`` or to provide new templates. Please contact Stephan Krusche and/or create Pull Requests in the GitHub repository.

.. _DejaGnu:

Java DejaGnu: Blackbox Testing
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The classical testing frameworks, like JUnit for example, allow writing whitebox tests.
This enforces many assumptions about the structure of the source code under test,
like the class and method names and even the exact method signatures.
This implies for a programming exercise that you have to specify all the structural aspects
if your test suite should be able to run on the student submissions.
That maybe is okay or even desired for a beginner course,
but for advanced courses this is a downside,
because without making their own decision about the structure of their source code,
the students cannot collect experience in this important aspect of programming through your exercises.

To be able to include the structural aspect of programming in your programming exercises,
without losing the ability to test submissions automatically, you can use `DejaGnu <https://www.gnu.org/software/dejagnu/>`__.
It allows you to write blackbox tests for command line interfaces of applications.
The tests are written in Expect Script, an extension to the Tcl scripting language.
Expect is a traditional Unix utility for automatic interactions with programs that expose a text terminal
interface in a robust way.
The test script starts the program to test as own process (possibly several times) and interactively controls
it by textual inputs via the standard input.
The script then reads the respective outputs and can make 'assertions' (e.g. exact or regex matching) on them.
Based on the obtained output it can also decide on the next inputs, thereby simulating how a user might interact with the program.
Thus, for the exercises you only have to specify the syntax of the command line interface
and at least a rough guidance for the output format.
The source code structure can be all up to the students as far as you want.

An assessment of the quality of the students' source code structure can be made with manual assessment after the submission deadline.
The template for this exercise type already uses Maven to compile the students' code.
Therefore, it could also be extended to additionally run regular unit tests (e.g. for `architecture tests <https://www.archunit.org/>`__ that ensure no cyclic package dependencies exist) and report the results for both to the student.

For the usage of DejaGnu consult the `official documentation <https://www.gnu.org/software/dejagnu/manual/index.html>`_
and the initial content of the test-repository created by Artemis for this exercise type.
The DejaGnu-specific files are stored inside the ``testsuite`` directory.
There, inside the ``….tests`` directory you can find three examples for test execution scripts.
For example a ``PROGRAM_test {add x} {}`` puts ``add x`` into the program and expects no output.
The ``PROGRAM_test`` and other shared helper functions are defined inside ``config/default.exp``.

The variables in screaming snake case in ``config/default.exp`` (e.g. ``MAIN_CLASS``) will be replaced to actual values in a previous build plan step.
For example, the build plan searches for the Java class with the main method and replaces ``MAIN_CLASS`` accordingly.
The best documentation of `Expect <https://core.tcl-lang.org/expect/>`_
can be found in the book `Exploring Expect <https://books.google.com.sg/books?id=t8C4pEDQ8s0C>`_.
However, to get started, the Artemis default template already contains quite a few reusable helper functions in ``config/default.exp`` for common input/output use-cases.

With this exercise type it is quite easy to reuse existing exercises of the
`Praktomat <https://github.com/KITPraktomatTeam/Praktomat>`_ autograder system with the Artemis platform.
