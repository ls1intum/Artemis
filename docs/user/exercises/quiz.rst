Quiz exercise
=============
.. contents:: Content of this document
    :local:
    :depth: 2

Overview
--------

- Prepare a Quiz Exercise: Only instructors and tutors can create, import, and export quiz exercises.

- Resolve a Quiz Exercise: Students can solve quiz exercises.

Prepare a quiz exercise
--------
This section describes the process to:

    - Create Quiz Exercises
    - Create Drag and Drop Model Quiz Exercises
    - Import Quiz Exercises from other courses
    - Export Quiz Exercises

Create new quiz exercise
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

- In the Artemis home page, click on Course Management button |CourseManagementButton|

- Navigate into **Exercises** of a specific course clicking in the exercise button.

    .. figure:: quiz/course-dashboard.png
            :align: center

- In the quiz exercises section, click on the Create Quiz button |CreateAQuizButton| to open the following form to create the new Quiz Exercise.

    .. figure:: quiz/create-a-quiz-form.png
            :align: center


    - **Title**: Provide a title for the quiz exercise (The red line means that this field is mandatory).

    - **Categories**: Type the category for the quiz exercise.

    - **Difficulty**: Select the difficult level among Easy, Medium and Hard. It is possible to select No level.

    - **Duration**: Provide the time in minutes and seconds for students solve of the Quiz Exercise.

    - **Options**: Chose between present the questions in Random order or not.

    - **Batch Mode**: Batch Mode controls how students can participate in the quiz.

        - Synchronized: There is exactly one opportunity for all students to participate in the quiz.

        - Batched: There are multiple opportunities for students to participate in the quiz by using a password.

        - Individual: Students can participate in the quiz by themselves at any time while it is released.

    - **Visible from**: The date and hour when the quiz becomes visible to students.

    - **Schedule Quiz Start**: To establish the date and hour that the quiz will be available to solve it.

        - **Start of working time**: Set the time when the students are able to see the questions and start answering. Students can start working on the quiz from this time until the duration ends.

    - **Should this exercise be included in the course score calculation?**:

        - Yes: the points will be included in the course score calculation
        - No: the points will not be included in the course score calculation.
        - Bonus: the points will be considered as bonus points.

    - **Questions**: There are four ways to add questions to a Quiz Exercise

        - **Add Multiple-Choice Question**

            This kind of question is composed by a problem statement with multiple options.

            .. figure:: quiz/multiple-choice-question.png
                :align: center

            - Short question title: Provide a short title (Mandatory).
            - Points: Assign the value points for this question.
            - Scoring type:

                - All or nothing
                - Proportional with Penalty
                - Proportional without Penalty

            - Present answer Options in Random order
            - Single Choice Mode: When there is just one correct option. This disable the Scoring type.
            - Delete icon: To delete the current question.
            - Edit View: Enables the text editor to write the quiz statement and its options, hints and explanations.
            - Edit bar: When the edit view is enable, the format bar allows to provide
                - Style to the statement text,
                - Correct Options [correct]
                - Incorrect Options [wrong]
                - Explanations [exp]
                - Hints [hint]
            - Text editor: The quiz statement can be developed with options, hints and explanations.
            - Preview View: Enables the student view.

        - **Add Drag-And-Drop Question**

            This kind of question is composed by a problem statement, a background image, and drag and drop options.

            .. figure:: quiz/drag-and-drop-question.png
                :align: center

            - Short question title: Provide a short title.
            - Points: Assign the value points for this question.
            - Scoring type:

                - All or nothing
                - Proportional with Penalty
                - Proportional without Penalty

            - Present Drag Items in Ransom order.
            - Delete icon: To delete the current question.
            - Edit View: Enables the text editor to write the question statement with explanations and hints.
            - Edit bar: When the edit view is enable, the format bar allows to provide
                - Style to the statement text,
                - Explanations [exp]
                - Hints [hint]
            - Text editor: The quiz statement can be developed with hints and explanations
            - Upload Background: To select and upload the background from the PC files to drag and drop the options over it.
            - Add Drag Items:
                - Text items: Type the options.
                - Image items: Can be uploaded from the PC files
            - Preview View: Enables the student view.

        - **Add Short-Answer Question**

            This kind of question is composed by a statement and spots to fill them out by tipping the answers.

            .. figure:: quiz/short-answer-question.png
                :align: center

            - Short question title: Provide a short title.
            - Points: Assign the value points for this question.
            - Scoring type:

                - All or nothing
                - Proportional with Penalty
                - Proportional without Penalty

            - Match Letter Case
            - Match Answers Exactly: This option moves the match slider to 100%.
            - Delete icon: To delete the current question.
            - Add Spot Option: To add the spot between the text to be fill out.
            - Add Answer Option: To provide the answer for each spot.
            - Text editor: The quiz statement can be developed with the spots and options.
            - Text view button: Enables the text editor to write and edit the question statement.
            - Preview View Button: Enables the student view.


        - **Add Existing Questions**
            This is a option that allows to import existing questions in other quiz exercises, courses, exams and from files.

            .. figure:: quiz/existing-question.png
                :align: center

            - Source buttons:
                - From a course
                - From an exam
                - From a file
            - List picker to select specific course, exam or file
            - Searching bar: to look for the question providing its name or part of it.
            - Filter options according the type of questions
                - Drag and Drop Questions
                - Multiple Choice Questions
                - Short answer Questions
            - Apply filter button
            - List of questions with the title, short title, and Type. In the Add column is possible to select all questions to be imported.
            - At the end of the list, click the Add selected Questions Button |AddSelectedQuestionsButton| to import all selected questions.

    - **Footer**: In the creation quiz page there is a footer with the following fields:

            .. figure:: quiz/footer.png
                :align: center
                :scale: 50

        - Error messages
        - Warning messages
        - Cancel Button
        - Save Button

Create Drag and Drop Model Quiz Exercise
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

    - In the quiz exercises section click on the Create Quiz button |CreateDragAndDropQuizButton|.

        .. figure:: quiz/apollon-diagrams.png
            :align: center

    - In the Apollon Diagrams page is possible to see the list of Apollon Diagrams and the possible actions to perform with them

        - Open
        - Delete

    - Clicking in the creation of a new Apollon Diagram button |CreateANewApollonDiagram| opens the following form

        .. figure:: quiz/Apollon-form.png
            :align: center
            :scale: 50

        - Title: provide the title of the Drag and Drop Model Quiz
        - Diagram Type: List picker that allows to select among several diagrams

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

        - Save Button
        - Cancel Button

    - Click the save button to open Apollon editor

        .. figure:: quiz/apollon-editor.png
            :align: center

        - Title: Allows to edit the tile of the diagram.
        - Crop image to selection: Allows to download the current selection.
        - Download Button: To download the selection
        - Generate a Quiz Exercise Button
        - Save Button
        - Modeling field: The items for modeling the diagram will be displayed here.
        - Elements to Drag and Drop in the Modeling field.


Import a Quiz
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

    - In the quiz exercises section, click on the Import a Quiz button |ImportQuizButton|

    - The list of existing quizzes will appear

        .. figure:: quiz/import-list-quizzes.png
            :align: center
            :scale: 50

        - The searching bar: Allows to look for a specific quiz by tipping its name or part of it.
        - The list of quizzes: Whit their ID, title, course and and indicator if they are exam questions.
        - By clicking the Import Button |ImportButton| opens the quiz editor with the existing questions. Here is possible to edit all parameter such as in the **Create new quiz exercise**.

Export a Quiz
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

    - In the quiz exercises section, click on the Export Quiz Exercises button |ExportQuizExerciseButton|

    - The list of quizzes will be shown

        .. figure:: quiz/export-quizzes-list.png
            :align: center
            :scale: 50

        - Select te quizzes for being exported in the Export Column.
        - The Export Button |ExportButton| will download the quiz in a JSON file.

Resolve a quiz exercise
--------
    - In the Artemis Homepage, if there is a quiz exercise as a current exercise, will be possible to see it from the course overview or inside any course.

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

    - If the quiz is set to start in a specific hour and the student open it before, he/she will se a message asking to wait until the quiz starts and the remaining time.

        .. figure:: quiz/please-wait-message.png
            :align: center
            :scale: 50

    - When the quiz starts, the student can see the questions and solve them.

        .. figure:: quiz/one-choice-question.png
            :align: center
            :scale: 50

    - The quiz page is compose by:

        - Number and title of the question
        - Points for solving that question
        - The quiz statement

        - Options:

            - Options with circles means one choice could be correct.
            - Options with squares means multiple options could be correct.

        - In the footer:

            - Number of questions and overall points
            - Time left to complete the quiz
            - Last time saved: The quiz will save all changes after they occur.
            - Connection status
            - Submit button: To allow student submit the quiz before the time ends.

        - In case of Drag and Drop questions, the items to be drag and drop in the option spots will be available in the right side.

        .. figure:: quiz/drag-and-drop-view.png
            :align: center
            :scale: 40

        - To submit and finish the quiz, the student must be press the submit button |SubmitButton|. However, when the quiz time's up, the answers will be submitted automatically.

    - The assessment is automatic and the student can see the result of the overall quiz and of specific questions. In the case of MC questions, the solution will be displayed.

        .. figure:: quiz/final-quiz.png
            :align: center
            :scale: 35

    - In the case of Drag and Drop questions, the solution is shown by clicking the show sample solution button |ShowSampleSolutionButton|.

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
