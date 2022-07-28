Quiz exercise
=============
.. contents:: Content of this document
    :local:
    :depth: 2

Overview
--------

- Prepare a Quiz Exercise: Only instructors and editors can create, import, and export quiz exercises.

- Resolve a Quiz Exercise: Students can solve quiz exercises.

Prepare a Quiz Exercise
--------
This section describes the process to:

    - Create Quiz Exercises
    - Create Drag and Drop Model Quiz Exercises
    - Import Quiz Exercises from other courses
    - Export Quiz Exercises

Create new Quiz Exercise
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

- On the Artemis home page, click on the Course Management button |CourseManagementButton|.

- Navigate into **Exercises** of a specific course by clicking on the exercise button.

    .. figure:: quiz/course-dashboard.png
            :align: center

- In the quiz exercises section, click on the Create Quiz button |CreateAQuizButton| to open the following form to create the new quiz exercise.

    .. figure:: quiz/create-a-quiz-form.png
            :align: center

    - **Title**: Provide a title for the quiz exercise (The red line means that this field is mandatory).

    - **Categories**: Type the category for the quiz exercise.

    - **Difficulty**: Select the difficulty level among Easy, Medium and Hard. It is possible to select No Level.

    - **Duration**: Provide the time in minutes and seconds for students to solve the quiz exercise.

    - **Options**: Choose between presenting the questions in random order or not.

    - **Batch Mode**: Batch Mode controls how students can participate in the quiz.

        - Synchronized: There is exactly one opportunity for all students to participate in the quiz.

        - Batched: There are multiple opportunities for students to participate in the quiz by using a password.

        - Individual: Students can participate in the quiz by themselves at any time while it is released.

    - **Visible from**: The date and hour when the quiz becomes visible to students.

    - **Schedule Quiz Start**: To establish the date and hour at which the quiz will be available for solving.

        - **Start of working time**: Set the time for the students to see the questions and start answering them. Students can start working on the quiz from this time until the duration ends.

    - **Should this exercise be included in the course score calculation?**:

        - Yes: the points will be included in the course score calculation.

        - No: the points will not be included in the course score calculation.

        - Bonus: the points will be considered as bonus points.

    - **Questions**: There are four ways to add questions to a quiz exercise.

        - **Add Multiple-Choice Question**

            This kind of question is composed of a problem statement with multiple options.

            .. figure:: quiz/multiple-choice-question.png
                :align: center

            - Short question title: Provide a short title (Mandatory).

            - Points: Assign the value points for this question.

            - Scoring type:

                - All or Nothing
                - Proportional with Penalty
                - Proportional without Penalty

            - Present answer options in random order.

            - Single Choice Mode: When there is just one correct option. This disables the Scoring type (resp. sets it to All or Nothing).

            - Delete icon: To delete the current question.

            - Edit View: Enables the text editor to write the quiz statement and its options, hints and explanations.

            - Edit bar: When the edit view is enabled, the format bar provides:

                - Style to the statement text

                - Correct Options [correct]

                - Incorrect Options [wrong]

                - Explanations [exp]

                - Hints [hint]

            - Text editor: The quiz statement can be developed with options, hints and explanations.

            - Preview View: Enables the student view.

        - **Add Drag-And-Drop Question**

            This kind of question is composed of a problem statement, a background image, and drag and drop options.

            .. figure:: quiz/drag-and-drop-question.png
                :align: center

            - Short question title: Provide a short title.

            - Points: Assign the value points for this question.

            - Scoring type:

                - All or Nothing

                - Proportional with Penalty

                - Proportional without Penalty

            - Present Drag Items in Random order.

            - Delete icon: To delete the current question.

            - Edit View: Enables the text editor to write the question statement with explanations and hints.

            - Edit bar: When the edit view is enabled, the format bar provides:

                - Style to the statement text

                - Explanations [exp]

                - Hints [hint]

            - Text editor: The quiz statement can be developed with hints and explanations.

            - Upload Background: To select and upload the background from the PC files to drag and drop the options over it.

            - Add Drag Items:

                - Text items: Type the options.

                - Image items: Can be uploaded from the PC files.

            - Preview View: Enables the student view.

        - **Add Short-Answer Question**

            This kind of question is composed of a statement and spots to fill them out by typing the answers.

            .. figure:: quiz/short-answer-question.png
                :align: center

            - Short question title: Provide a short title.

            - Points: Assign the value points for this question.

            - Scoring type:

                - All or Nothing

                - Proportional with Penalty

                - Proportional without Penalty

            - Match Letter Case

            - Match Answers Exactly: This option moves the match slider to 100%.

            - Delete icon: To delete the current question.

            - Add Spot Option: To add the spot between the text to be filled out.

            - Add Answer Option: To provide the answer for each spot.

            - Text editor: The quiz statement can be developed with the spots and options.

            - Text View: Enables the text editor to write and edit the question statement.

            - Visual View: Enables the student view.

        - **Add Existing Questions**

            This option allows to import existing questions from other quiz exercises, courses, exams and files.

            .. figure:: quiz/existing-question.png
                :align: center

            - Source buttons:

                - From a course

                - From an exam

                - From a file

            - List picker to select a specific course, exam or file.

            - Searching bar: to look for the question providing its name or part of it.

            - Filter options according to the type of questions:

                - Drag and Drop Question

                - Multiple Choice Question

                - Short Answer Question

            - Apply filter button

            - List of questions with the title, short title, and Type. In the Add column, it is possible to select all questions to be imported.

            - At the end of the list, click the Add selected Questions button |AddSelectedQuestionsButton| to import all selected questions.

    - **Footer**: On the creation quiz page there is a footer with the following fields:

            .. figure:: quiz/footer.png
                :align: center
                :scale: 50

        - Error messages

        - Warning messages

        - Cancel button

        - Save button

Create Drag and Drop Model Quiz Exercise
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

    - In the quiz exercises section click on the Create Quiz button |CreateDragAndDropQuizButton|.

        .. figure:: quiz/apollon-diagrams.png
            :align: center

    - On the Apollon Diagrams page, it is possible to see the list of Apollon Diagrams and the possible actions to perform with them.

        - Open
        - Delete

    - Clicking on the creation of a new Apollon Diagram button |CreateANewApollonDiagram| opens the following form:

        .. figure:: quiz/Apollon-form.png
            :align: center
            :scale: 50

        - Title: provide the title of the Drag-and-Drop Model Quiz

        - Diagram Type: It is a list picker to select between several diagrams:

            - Class Diagram

            - Activity Diagram

            - Object Diagram

            - Use Case Diagram

            - Communication Diagram

            - Component Diagram

            - Deployment Diagram

            - Petri Net

            - Syntax Tree

            - Flowchart

        - Save button

        - Cancel button

    - Click the save button to open the Apollon editor

        .. figure:: quiz/apollon-editor.png
            :align: center

        - Title: Allows to edit the tile of the diagram.

        - Crop image to selection: Allows to download the current selection.

        - Download button: To download the selection.

        - Generate a quiz exercise button.

        - Save button.

        - Modeling field: The items for modeling the diagram will be displayed here.

        - Elements to Drag and Drop in the Modeling field.

Import a Quiz
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

    - In the quiz exercises section, click on the Import a Quiz button |ImportQuizButton|.

    - The list of existing quizzes will appear.

        .. figure:: quiz/import-list-quizzes.png
            :align: center
            :scale: 50

        - The searching bar: Allows to look for a specific quiz by typing its name or part of it.

        - The list of quizzes: Whit their ID, title, course and indicator if they are exam questions.

        - Clicking the Import button |ImportButton| opens the quiz editor with the existing questions. Here it is possible to edit all parameters such as in **Create new Quiz Exercise**.

Export a Quiz
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

    - In the quiz exercises section, click on the Export Quiz Exercises button |ExportQuizExerciseButton| and the list of quizzes will be shown

        .. figure:: quiz/export-quizzes-list.png
            :align: center
            :scale: 50

        - Select the quizzes for being exported in the Export column.

        - The Export button |ExportButton| will download the quiz in a JSON file.

Resolve a Quiz Exercise
--------

    - If a quiz exercise is available on the Artemis home page as a current exercise, it will be possible to see it in the course overview or inside the course where it belongs.

        .. figure:: quiz/current-quiz-exercise.png
            :align: center
            :scale: 50

    - The current exercise box will show:

        - The name of the quiz

        - The button to start the quiz

        - The category

        - The message if the quiz is active

        - The due date

    - To start the quiz, the student must press the Open quiz button |OpenQuizButton|.

    - If the quiz is set to start at a specific time and the student opens it before, he/she will see a message asking to wait until the quiz starts and displaying the remaining time.

        .. figure:: quiz/please-wait-message.png
            :align: center
            :scale: 50

    - When the quiz starts, the student can see the questions and solve them.

        .. figure:: quiz/one-choice-question.png
            :align: center
            :scale: 50

    - The quiz page is composed of:

        - Number and title of the question

        - Points for solving that question

        - The quiz statement

        - Options:

            - Options with circles mean one choice could be correct.

            - Options with squares mean multiple options could be correct.

        - In the footer:

            - The number of questions and overall points.

            - Time left to complete the quiz.

            - Last time saved: The quiz will save all changes after they occur.

            - Connection status.

            - Submit button: To allow the student to submit the quiz before the time ends.

        - In the case of Drag and Drop questions, the items to be dragged and dropped in the spots will be available on the right side.

        .. figure:: quiz/drag-and-drop-view.png
            :align: center
            :scale: 40

        - To submit and finish the quiz, the student must press the Submit button |SubmitButton|. However, when the quiz time's up, the answers will be submitted automatically.

    - The assessment is automatic and the student can see the result of the overall quiz and of specific questions. In the case of MC questions, the solution will be displayed.

        .. figure:: quiz/final-quiz.png
            :align: center
            :scale: 35

    - In the case of Drag and Drop questions, the solution is shown by clicking the Show Sample Solution button |ShowSampleSolutionButton|.

        .. figure:: quiz/solution-drag-and-drop.png
            :align: center
            :scale: 40

.. |CourseManagementButton| image:: quiz/CourseManagementButton.png
    :scale: 50
.. |AddSelectedQuestionsButton| image:: quiz/add-selected-questions-button.png
    :scale: 50
.. |CreateANewApollonDiagram| image:: quiz/create-a-new-apollon-diagram.png
    :scale: 50
.. |CreateAQuizButton| image:: quiz/create-quiz-button.png
    :scale: 50
.. |CreateDragAndDropQuizButton| image:: quiz/create-drag-and-drop-quiz.png
    :scale: 50
.. |ImportQuizButton| image:: quiz/import-quiz-button.png
    :scale: 50
.. |ImportButton| image:: quiz/import-button.png
    :scale: 50
.. |ExportQuizExerciseButton| image:: quiz/export-quiz-button.png
    :scale: 50
.. |ExportButton| image:: quiz/export-button.png
    :scale: 50
.. |OpenQuizButton| image:: quiz/open-quiz-button.png
    :scale: 50
.. |SubmitButton| image:: quiz/submit-button.png
    :scale: 50
.. |ShowSampleSolutionButton| image:: quiz/show-sample-solution.png
    :scale: 50
