.. _programming_features:

Artemis and its version control and continuous integration infrastructure is independent of the programming language and thus supports
teaching and learning with any programming language that can be compiled and tested on the command line.
Instructors have a lot of freedom in defining the environment (e.g. using build agents and Docker images) in which student code is executed and tested.
To simplify the setup of programming exercises, Artemis supports several ``templates`` that show how the setup works.
Instructors can still use those templates to generate programming exercises and then adapt and customize the settings in the repositories and build plans.


- The support for a specific programming language ``templates`` depends on the used ``continuous integration`` system. The table below gives an overview:

  +----------------------+--------+---------+-----------+----------+
  | Programming Language | Bamboo | Jenkins | GitLab CI | Local CI |
  +======================+========+=========+===========+==========+
  | Java                 | yes    | yes     | yes       | yes      |
  +----------------------+--------+---------+-----------+----------+
  | Python               | yes    | yes     | no        | no       |
  +----------------------+--------+---------+-----------+----------+
  | C                    | yes    | yes     | no        | no       |
  +----------------------+--------+---------+-----------+----------+
  | Haskell              | yes    | yes     | no        | no       |
  +----------------------+--------+---------+-----------+----------+
  | Kotlin               | yes    | yes     | no        | no       |
  +----------------------+--------+---------+-----------+----------+
  | VHDL                 | yes    | no      | no        | no       |
  +----------------------+--------+---------+-----------+----------+
  | Assembler            | yes    | no      | no        | no       |
  +----------------------+--------+---------+-----------+----------+
  | Swift                | yes    | yes     | no        | no       |
  +----------------------+--------+---------+-----------+----------+
  | OCaml                | yes    | no      | no        | no       |
  +----------------------+--------+---------+-+---------+----------+

- Not all ``templates`` support the same feature set and supported features can also change depending on the continuous integration system setup.
  Depending on the feature set, some options might not be available during the creation of the programming exercise.
  The table below provides an overview of the supported features.
  In case a feature has different support for different continuous integration systems, the table shows the differences between Bamboo **(B)**, Jenkins **(J)**, GitLab CI **(G)**, and Local CI **(L)**.

  +----------------------+----------------------+----------------------+---------------------+--------------+------------------------------------------+------------------------------+----------------------------+------------------------+------------------------+
  | Programming Language | Sequential Test Runs | Static Code Analysis | Plagiarism Check    | Package Name | Project Type                             | Solution Repository Checkout | Testwise Coverage Analysis | Publish Build Plan URL | Auxiliary repositories |
  +======================+======================+======================+=====================+==============+==========================================+==============================+============================+========================+========================+
  | Java                 | B, J: yes; G, L: no  | B, J: yes; G, L: no  | B, J: yes; G, L: no | yes          | B, J: Gradle, Maven; G: Maven; L: Gradle | no                           | B, J, G: yes; L: no        | B, J, G: yes; L: no    | B: yes; J, G, L: no    |
  +----------------------+----------------------+----------------------+---------------------+--------------+------------------------------------------+------------------------------+----------------------------+------------------------+------------------------+
  | Python               | B: yes; J: no        | no                   | yes                 | no           | n/a                                      | no                           | no                         | yes                    | yes                    |
  +----------------------+----------------------+----------------------+---------------------+--------------+------------------------------------------+------------------------------+----------------------------+------------------------+------------------------+
  | C                    | no                   | B: yes; J: no        | yes                 | no           | FACT, GCC                                | no                           | no                         | yes                    | yes                    |
  +----------------------+----------------------+----------------------+---------------------+--------------+------------------------------------------+------------------------------+----------------------------+------------------------+------------------------+
  | C (FACT framework)   | no                   | B: yes; J: no        | yes                 | no           | n/a                                      | no                           | no                         | yes                    | yes                    |
  +----------------------+----------------------+----------------------+---------------------+--------------+------------------------------------------+------------------------------+----------------------------+------------------------+------------------------+
  | Haskell              | B: yes; J: no        | no                   | no                  | no           | n/a                                      | B: yes; J: no                | no                         | yes                    | yes                    |
  +----------------------+----------------------+----------------------+---------------------+--------------+------------------------------------------+------------------------------+----------------------------+------------------------+------------------------+
  | Kotlin               | yes                  | no                   | yes                 | yes          | n/a                                      | no                           | yes                        | yes                    | yes                    |
  +----------------------+----------------------+----------------------+---------------------+--------------+------------------------------------------+------------------------------+----------------------------+------------------------+------------------------+
  | VHDL                 | no                   | no                   | no                  | no           | n/a                                      | no                           | no                         | yes                    | yes                    |
  +----------------------+----------------------+----------------------+---------------------+--------------+------------------------------------------+------------------------------+----------------------------+------------------------+------------------------+
  | Assembler            | no                   | no                   | no                  | no           | n/a                                      | no                           | no                         | yes                    | yes                    |
  +----------------------+----------------------+----------------------+---------------------+--------------+------------------------------------------+------------------------------+----------------------------+------------------------+------------------------+
  | Swift                | no                   | yes                  | yes                 | yes          | B: Plain, Xcode; J: Plain                | no                           | no                         | yes                    | yes                    |
  +----------------------+----------------------+----------------------+---------------------+--------------+------------------------------------------+------------------------------+----------------------------+------------------------+------------------------+
  | OCaml                | no                   | no                   | no                  | no           | n/a                                      | yes                          | no                         | yes                    | yes                    |
  +----------------------+----------------------+----------------------+---------------------+--------------+------------------------------------------+------------------------------+----------------------------+------------------------+------------------------+

  - *Sequential Test Runs*: ``Artemis`` can generate a build plan which first executes structural and then behavioral tests. This feature can help students to better concentrate on the immediate challenge at hand.
  - *Static Code Analysis*: ``Artemis`` can generate a build plan which additionally executes static code analysis tools.
    ``Artemis`` categorizes the found issues and provides them as feedback for the students. This feature makes students aware of code quality issues in their submissions.
  - *Plagiarism Checks*: ``Artemis`` is able to automatically calculate the similarity between student submissions. A side-by-side view of similar submissions is available to confirm the plagiarism suspicion.
  - *Package Name*: A package name has to be provided
  - *Solution Repository Checkout*: Instructors are able to compare a student submission against a sample solution in the solution repository
  - *Testwise Coverage Analysis*: ``Artemis`` can generate a build plan which additionally executes a testwise coverage analysis.
    ``Artemis`` aggregates the recorded data into different metrics. This feature allows instructors to check which code in the solution submission is how often executed by the test cases.
  - *Publish Build Plan*: ``Artemis`` can display the URL to the build plan (e.g. on Bamboo or Jenkins) to the student. This will not work for the upcoming internal continuous integration system that has no graphical user interface.

.. note::
  Only some ``templates`` for ``Bamboo`` support ``Sequential Test Runs`` at the moment.

.. note::
  Static Code Analysis for ``C`` exercises is only supported for ``Bamboo`` at the moment.

.. note::
  Testwise Coverage Analysis is only supported for ``Bamboo`` and exercises with regular test runs at the moment.

.. note::
  Instructors are still able to extend the generated programming exercises with additional features that are not available in one specific template.

.. note::
  Documentation for FACT framework can be found here: https://fact.readthedocs.io/en/latest/

We encourage instructors to contribute improvements to the existing ``templates`` or to provide new templates. Please contact Stephan Krusche and/or create Pull Requests in the GitHub repository.

