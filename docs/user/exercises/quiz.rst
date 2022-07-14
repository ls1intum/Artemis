Quiz exercise
=============
.. contents:: Content of this document
    :local:
    :depth: 2

Overview
--------

1. Prepare a quiz exercise (Instructor)

2. Resolve a quiz exercise (Student)

Prepare a quiz exercise
--------
This section describes the process to create a quiz exercise.

- In the home page, click on Course Management button |CourseManagementButton|
- Navigate into **Exercises** of your preferred course clicking in the exercise button.

    .. figure:: quiz/course-dashboard.png
            :align: center

Create new quiz exercise
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

- In the quiz exercises part click on the Create Quiz button |CreateAQuizButton|

- The following form will be open for create the quiz.

    .. figure:: quiz/create-a-quiz-form.png
            :align: center

    - **Title**: Title of the quiz exercise
    - **Categories**: Category of the quiz exercise
    - **Difficulty**: Difficulty of the quiz exercise: No level, Easy, Medium or Hard
    - **Duration**: Time to solve of the quiz exercise
    - **Options**: To chose between present the questions in Random order or not.
    - **Batch Mode**: Batch Mode controls how students can participate in the quiz.

        - Synchronized: There is exactly opportunity for all students to participate in the quiz at once.
        - Batched: There are multiple opportunities for students to participate in the quiz by using a password.
        - Individual: Students can participate in the quiz by themselves at any time while it is released..

    - **Visible from**: The date when the quiz becomes visible to students. Students can start working on the quiz from this time onward.
    - **Schedule Quiz Start**: To make available the start working time to solve teh quiz.

        - **Start of working time**: Set the time when the students are able to see the questions and start answering.
    - **Should this exercise be included in the course score calculation?**:
        - Yes: the points will be included in the course score calculation
        - No: the points will be included in the course score calculation.
        - Bonus: the points will be bonus points.
    - **Questions**:
        - Add Multiple-Choice Question

            .. figure:: quiz/multiple-choice-question.png
                :align: center

            - Short question title
            - Points
            - Scoring type
            - Present answer Options in Ransom order
            - Single Choice Mode
            - Delete icon
            - Edit View
            - Preview View
            - Format bar
            - Text editor

        - Add Drag-And-Drop Question

            .. figure:: quiz/drag-and-drop-question.png
                :align: center

            - Short question title
            - Points
            - Scoring type
            - Present Drag Items in Ransom order
            - Delete icon
            - Edit View
            - Format bar
            - Text editor
            - Upload Background
            - Add Drag Items
            - Preview View

        - Add Short-Answer Question

            .. figure:: quiz/short-answer-question.png
                :align: center

            - Short question title
            - Points
            - Scoring type
            - Match Letter Case
            - Match Answers Exactly
            - Delete icon
            - Add Spot and Answer Option
            - Edit Text Field
            - Text and Preview View Buttons


        - Add Existing Questions
            This option allows to insert questions from: a course, an exam and a file.

            .. figure:: quiz/existing-question.png
                :align: center

            - List picker to select the course, exam or file
            - Searching bar
            - Filter options according the type of questions
            - Apply filter button
            - List of questions

            Select in the add column the questions to be added and and the end of the list click the Add selected Questions Button |AddSelectedQuestionsButton|.

    - **Footer**: In the footer of the page it is visible:

            .. figure:: quiz/footer.png
                :align: center
                :scale: 50

        - Error messages
        - Warning messages
        - Cancel Button
        - Save Button

Create Drag and Drop Model Quiz
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

    - In the quiz exercises part click on the Create Quiz button |CreateDragAndDropQuizButton|

    - In the Apollon Diagrams page is possible to see the list of Apollon Diagrams and the possible actions to perform with them: Open and Delete.

        .. figure:: quiz/apollon-diagrams.png
            :align: center

    - Clicking in the creation of a new Apollon Diagram button |CreateANewApollonDiagram| opens the following form

        .. figure:: quiz/Apollon-form.png
            :align: center
            :scale: 50

        - Title of the Drag and Drop Model Quiz
        - Diagram Type list picker allows to select among:

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

        - Title
        - Crop image to selection
        - Download Button
        - Generate a Quiz Exercise Button
        - Save Button
        - Modeling field
        - Elements to Drag and Drop in the Modeling field.


Import a Quiz
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

    - In the quiz exercises part, click on the Import a Quiz button |ImportQuizButton|

    - The list of existing quizzes will appear

        .. figure:: quiz/import-list-quizzes.png
            :align: center
            :scale: 50

        - The searching bar
        - The list of quizzes
        - By clicking the Import Button |ImportButton| opens the quiz editor with the existing questions. Here is possible to edit all parameter such as in the **Create new quiz exercise**.

Export a Quiz
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

    - In the quiz exercises part, click on the Export Quiz Exercises button |ExportQuizExerciseButton|

    - The list of quizzes will be shown

        .. figure:: quiz/export-quizzes-list.png
            :align: center
            :scale: 50

        - Select te quizzes for being exported
        - The Export Button |ExportButton| will download the quiz in a JSON file.



Resolve a quiz exercise
--------
    - If there is a quiz exercise as a current exercise, will be possible to see it from the course overview or inside any course.

        .. figure:: quiz/current-quiz-exercise.png
            :align: center
            :scale: 50

        - The name of the quiz
        - The button to start the quiz
        - The category
        - The message if the quiz is active
        - The due date

    - To start the quiz, the student must press the Open Quiz Button |OpenQuizButton|

    - If the quiz is set to start in a specific hour, the student will se a message asking to wait

        .. figure:: quiz/please-wait-message.png
            :align: center
            :scale: 50

    - When the quiz starts, the student can see and solve the questions.

        .. figure:: quiz/one-choice-question.png
            :align: center
            :scale: 50

        - Number and title of the question
        - Points for solving that question
        - The quiz statement

        - Options
            - Options with circles means one choice could be correct.
            - Options with squares means multiple options could be correct.

        - In the footer:

            - Number of questions and overall points
            - Time left to complete the quiz
            - Last time saved: The quiz will be save after any chenge.
            - Connection status
            - Submit button.

        - In case of Drag and Drop questions, in the right side will be the items to be dran and drop in the option spots.

        .. figure:: quiz/drag-and-drop-view.png
            :align: center
            :scale: 40

        - To submit and finish the quiz, the student must be press the submit button |SubmitButton|. However, when the quiz time's up, the answers will be submitted automatically.

    - The assessment is automatic and the student can see the results.

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
