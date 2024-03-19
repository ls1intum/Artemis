import dayjs from 'dayjs/esm';

import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { Exercise } from 'app/entities/exercise.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { Participation } from 'app/entities/participation/participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { TextExercise } from 'app/entities/text-exercise.model';

import fileUploadExerciseTemplate from '../../fixtures/exercise/file-upload/template.json';
import modelingExerciseSubmissionTemplate from '../../fixtures/exercise/modeling/submission.json';
import modelingExerciseTemplate from '../../fixtures/exercise/modeling/template.json';
import cProgrammingExerciseTemplate from '../../fixtures/exercise/programming/c/template.json';
import javaAssessmentSubmission from '../../fixtures/exercise/programming/java/assessment/submission.json';
import javaProgrammingExerciseTemplate from '../../fixtures/exercise/programming/java/template.json';
import pythonProgrammingExerciseTemplate from '../../fixtures/exercise/programming/python/template.json';
import multipleChoiceSubmissionTemplate from '../../fixtures/exercise/quiz/multiple_choice/submission.json';
import shortAnswerSubmissionTemplate from '../../fixtures/exercise/quiz/short_answer/submission.json';
import quizTemplate from '../../fixtures/exercise/quiz/template.json';
import textExerciseTemplate from '../../fixtures/exercise/text/template.json';
import {
    BASE_API,
    COURSE_BASE,
    DELETE,
    EXERCISE_BASE,
    ExerciseType,
    GET,
    MODELING_EXERCISE_BASE,
    POST,
    PROGRAMMING_EXERCISE_BASE,
    PUT,
    ProgrammingExerciseAssessmentType,
    ProgrammingLanguage,
    QUIZ_EXERCISE_BASE,
    TEXT_EXERCISE_BASE,
    UPLOAD_EXERCISE_BASE,
} from '../constants';
import { dayjsToString, generateUUID, titleLowercase } from '../utils';

/**
 * A class which encapsulates all API requests related to exercises.
 */
export class ExerciseAPIRequests {
    /**
     * Creates a programming exercise with the specified title and other data
     * @param options An object containing the options for creating the programming exercise
     *   - course: The course the exercise will be added to
     *   - exerciseGroup: The exercise group the exercise will be added to
     *   - scaMaxPenalty: The max percentage (0-100) static code analysis can reduce from the points
     *                    If sca should be disabled, pass null or omit this property
     *   - recordTestwiseCoverage: Enable testwise coverage analysis for this exercise
     *   - releaseDate: When the programming exercise should be available
     *   - dueDate: When the programming exercise should be due
     *   - title: The title of the programming exercise
     *   - programmingShortName: The short name of the programming exercise
     *   - programmingLanguage: The programming language for the exercise
     *   - packageName: The package name of the programming exercise
     *   - assessmentDate: The due date of the assessment
     *   - assessmentType: The assessment type of the exercise
     * @returns <Chainable> request response
     */
    createProgrammingExercise(options: {
        course?: Course;
        exerciseGroup?: ExerciseGroup;
        scaMaxPenalty?: number | null;
        recordTestwiseCoverage?: boolean;
        releaseDate?: dayjs.Dayjs;
        dueDate?: dayjs.Dayjs;
        title?: string;
        programmingShortName?: string;
        programmingLanguage?: ProgrammingLanguage;
        packageName?: string;
        assessmentDate?: dayjs.Dayjs;
        assessmentType?: ProgrammingExerciseAssessmentType;
    }): Cypress.Chainable<Cypress.Response<ProgrammingExercise>> {
        const {
            course,
            exerciseGroup,
            scaMaxPenalty = null,
            recordTestwiseCoverage = false,
            releaseDate = dayjs(),
            dueDate = dayjs().add(1, 'day'),
            title = 'Programming ' + generateUUID(),
            programmingShortName = 'programming' + generateUUID(),
            programmingLanguage = ProgrammingLanguage.JAVA,
            packageName = 'de.test',
            assessmentDate = dayjs().add(2, 'days'),
            assessmentType = ProgrammingExerciseAssessmentType.AUTOMATIC,
        } = options;

        let programmingExerciseTemplate = {};

        if (programmingLanguage == ProgrammingLanguage.PYTHON) {
            programmingExerciseTemplate = pythonProgrammingExerciseTemplate;
        } else if (programmingLanguage == ProgrammingLanguage.C) {
            programmingExerciseTemplate = cProgrammingExerciseTemplate;
        } else if (programmingLanguage == ProgrammingLanguage.JAVA) {
            programmingExerciseTemplate = javaProgrammingExerciseTemplate;
        }

        const exercise = {
            ...programmingExerciseTemplate,
            title,
            shortName: programmingShortName,
            packageName,
            channelName: 'exercise-' + titleLowercase(title),
            assessmentType: ProgrammingExerciseAssessmentType[assessmentType],
            ...(course ? { course } : {}),
            ...(exerciseGroup ? { exerciseGroup } : {}),
        } as ProgrammingExercise;

        if (!exerciseGroup) {
            exercise.releaseDate = releaseDate;
            exercise.dueDate = dueDate;
            exercise.assessmentDueDate = assessmentDate;
        }

        if (scaMaxPenalty) {
            exercise.staticCodeAnalysisEnabled = true;
            exercise.maxStaticCodeAnalysisPenalty = scaMaxPenalty;
        }

        exercise.programmingLanguage = programmingLanguage;
        exercise.testwiseCoverageEnabled = recordTestwiseCoverage;

        return cy.request({
            url: `${PROGRAMMING_EXERCISE_BASE}/setup`,
            method: POST,
            body: exercise,
        });
    }

    /**
     * Submits the example submission to the specified repository.
     *
     * @param repositoryId - The repository ID. The repository ID is equal to the participation ID.
     * @returns A Cypress.Chainable<Cypress.Response<any>> representing the API request response.
     */
    makeProgrammingExerciseSubmission(repositoryId: number) {
        // TODO: For now it is enough to submit the one prepared json file, but in the future this method should support different package names and submissions.
        return cy.request({
            url: `${BASE_API}/repository/${repositoryId}/files?commit=yes`,
            method: PUT,
            body: javaAssessmentSubmission,
        });
    }

    /**
     * Adds a text exercise to an exercise group in an exam or to a course.
     *
     * @param body - An object containing either the course or exercise group the exercise will be added to.
     * @param title - The title for the text exercise (optional, default: auto-generated).
     * @returns A Cypress.Chainable<Cypress.Response<TextExercise>> representing the API request response.
     */
    createTextExercise(body: { course: Course } | { exerciseGroup: ExerciseGroup }, title = 'Text ' + generateUUID()): Cypress.Chainable<Cypress.Response<TextExercise>> {
        const template = {
            ...textExerciseTemplate,
            title,
            channelName: 'exercise-' + titleLowercase(title),
        };
        const textExercise = Object.assign({}, template, body);
        return cy.request({ method: POST, url: TEXT_EXERCISE_BASE, body: textExercise });
    }

    /**
     * Deletes a text exercise with the specified exercise ID.
     *
     * @param exerciseId - The ID of the text exercise to be deleted.
     * @returns A Cypress.Chainable<Cypress.Response<any>> representing the API request response.
     */
    deleteTextExercise(exerciseId: number) {
        return cy.request({
            url: `${TEXT_EXERCISE_BASE}/${exerciseId}`,
            method: DELETE,
        });
    }

    /**
     * Makes a text exercise submission for the specified exercise ID with the given text content.
     *
     * @param exerciseId - The ID of the text exercise for which the submission is made.
     * @param text - The text content of the submission.
     * @returns A Cypress.Chainable<Cypress.Response<any>> representing the API request response.
     */
    makeTextExerciseSubmission(exerciseId: number, text: string) {
        return cy.request({
            url: `${EXERCISE_BASE}/${exerciseId}/text-submissions`,
            method: PUT,
            body: { submissionExerciseType: 'text', text },
        });
    }

    /**
     * Creates a file upload exercise.
     *
     * @param body - An object containing either the course or exercise group the exercise will be added to.
     * @param title - The title for the exercise (optional, default: auto-generated).
     * @returns A Cypress.Chainable<Cypress.Response<FileUploadExercise>> representing the API request response.
     */
    createFileUploadExercise(
        body: { course: Course } | { exerciseGroup: ExerciseGroup },
        title = 'Upload ' + generateUUID(),
    ): Cypress.Chainable<Cypress.Response<FileUploadExercise>> {
        const template = {
            ...fileUploadExerciseTemplate,
            title,
            channelName: 'exercise-' + titleLowercase(title),
        };
        const uploadExercise = Object.assign({}, template, body);
        return cy.request({ method: POST, url: UPLOAD_EXERCISE_BASE, body: uploadExercise });
    }

    /**
     * Deletes a file upload exercise with the specified exercise ID.
     *
     * @param exerciseID - The ID of the file upload exercise to be deleted.
     * @returns A Cypress.Chainable<Cypress.Response<any>> representing the API request response.
     */
    deleteFileUploadExercise(exerciseID: number) {
        return cy.request({
            url: `${UPLOAD_EXERCISE_BASE}/${exerciseID}`,
            method: DELETE,
        });
    }

    /**
     * Makes a file upload exercise submission for the specified exercise ID with the given file.
     *
     * @param exerciseId - The ID of the file upload exercise for which the submission is made.
     * @param file - The file content of the submission.
     * @returns A Cypress.Chainable<Cypress.Response<any>> representing the API request response.
     */
    makeFileUploadExerciseSubmission(exerciseId: number, file: string) {
        return cy.request({
            url: `${EXERCISE_BASE}/${exerciseId}/file-upload-submissions`,
            method: POST,
            body: { submissionExerciseType: 'file-upload', file },
        });
    }

    /**
     * Creates a modeling exercise.
     *
     * @param body - An object containing either the course or exercise group the exercise will be added to.
     * @param title - The title for the exercise (optional, default: auto-generated).
     * @param releaseDate - The release date of the exercise (optional, default: current date).
     * @param dueDate - The due date of the exercise (optional, default: current date + 1 day).
     * @param assessmentDueDate - The assessment due date of the exercise (optional, default: current date + 2 days).
     * @returns A Cypress.Chainable<Cypress.Response<ModelingExercise>> representing the API request response.
     */
    createModelingExercise(
        body: { course: Course } | { exerciseGroup: ExerciseGroup },
        title = 'Modeling ' + generateUUID(),
        releaseDate = dayjs(),
        dueDate = dayjs().add(1, 'days'),
        assessmentDueDate = dayjs().add(2, 'days'),
    ): Cypress.Chainable<Cypress.Response<ModelingExercise>> {
        const templateCopy = {
            ...modelingExerciseTemplate,
            title,
            channelName: 'exercise-' + titleLowercase(title),
        };
        const dates = {
            releaseDate: dayjsToString(releaseDate),
            dueDate: dayjsToString(dueDate),
            assessmentDueDate: dayjsToString(assessmentDueDate),
        };
        let newModelingExercise;
        // eslint-disable-next-line no-prototype-builtins
        if (body.hasOwnProperty('course')) {
            newModelingExercise = Object.assign({}, templateCopy, dates, body);
        } else {
            newModelingExercise = Object.assign({}, templateCopy, body);
        }
        return cy.request({
            url: MODELING_EXERCISE_BASE,
            method: POST,
            body: newModelingExercise,
        });
    }

    /**
     * Updates the assessment due date of a modeling exercise.
     *
     * @param exercise - The modeling exercise to update.
     * @param due - The new assessment due date (optional, default: current date).
     * @returns A Cypress.Chainable<Cypress.Response<any>> representing the API request response.
     */
    updateModelingExerciseAssessmentDueDate(exercise: ModelingExercise, due = dayjs()) {
        exercise.assessmentDueDate = due;
        return this.updateExercise(exercise, ExerciseType.MODELING);
    }

    /**
     * Deletes a modeling exercise with the specified exercise ID.
     *
     * @param exerciseID - The ID of the modeling exercise to be deleted.
     * @returns A Cypress.Chainable<Cypress.Response<any>> representing the API request response.
     */
    deleteModelingExercise(exerciseID: number) {
        return cy.request({
            url: `${MODELING_EXERCISE_BASE}/${exerciseID}`,
            method: DELETE,
        });
    }

    /**
     * Makes a modeling exercise submission for the specified exercise ID and participation.
     *
     * @param exerciseID - The ID of the modeling exercise for which the submission is made.
     * @param participation - The participation data for the submission.
     * @returns A Cypress.Chainable<Cypress.Response<any>> representing the API request response.
     */
    makeModelingExerciseSubmission(exerciseID: number, participation: Participation) {
        return cy.request({
            url: `${EXERCISE_BASE}/${exerciseID}/modeling-submissions`,
            method: PUT,
            body: {
                ...modelingExerciseSubmissionTemplate,
                id: participation.submissions![0].id,
                participation,
            },
        });
    }

    /**
     * Updates the due date of a modeling exercise.
     *
     * @param exercise - The modeling exercise to update.
     * @param due - The new due date (optional, default: current date).
     * @returns A Cypress.Chainable<Cypress.Response<any>> representing the API request response.
     */
    updateModelingExerciseDueDate(exercise: ModelingExercise, due = dayjs()) {
        exercise.dueDate = due;
        return this.updateExercise(exercise, ExerciseType.MODELING);
    }

    /**
     * Creates a quiz exercise.
     *
     * @param body - An object containing either the course or exercise group the exercise will be added to.
     * @param quizQuestions - A list of quiz question objects that make up the quiz (e.g., multiple choice, short answer, or drag and drop).
     * @param title - The title for the quiz exercise (optional, default: auto-generated).
     * @param releaseDate - The release date of the quiz exercise (optional, default: current date + 1 year).
     * @param duration - The duration in seconds that students get to complete the quiz (optional, default: 600 seconds).
     * @returns A Cypress.Chainable<Cypress.Response<QuizExercise>> representing the API request response.
     */
    createQuizExercise(
        body: { course: Course } | { exerciseGroup: ExerciseGroup },
        quizQuestions: [any],
        title = 'Quiz ' + generateUUID(),
        releaseDate = dayjs().add(1, 'year'),
        duration = 600,
    ): Cypress.Chainable<Cypress.Response<QuizExercise>> {
        const quizExercise: any = {
            ...quizTemplate,
            title,
            quizQuestions,
            duration,
            channelName: 'exercise-' + titleLowercase(title),
        };
        let newQuizExercise;
        const dates = {
            releaseDate: dayjsToString(releaseDate),
        };
        // eslint-disable-next-line no-prototype-builtins
        if (body.hasOwnProperty('course')) {
            newQuizExercise = Object.assign({}, quizExercise, dates, body);
        } else {
            newQuizExercise = Object.assign({}, quizExercise, body);
        }

        const formData = new FormData();
        formData.append('exercise', new File([JSON.stringify(newQuizExercise)], 'exercise', { type: 'application/json' }));

        return cy.request({
            url: QUIZ_EXERCISE_BASE,
            method: POST,
            body: formData,
        });
    }

    /**
     * Deletes a quiz exercise with the specified exercise ID.
     *
     * @param exerciseId - The ID of the quiz exercise to be deleted.
     * @returns A Cypress.Chainable<Cypress.Response<any>> representing the API request response.
     */
    deleteQuizExercise(exerciseId: number) {
        return cy.request({
            url: `${QUIZ_EXERCISE_BASE}/${exerciseId}`,
            method: DELETE,
        });
    }

    /**
     * Sets a quiz exercise as visible.
     *
     * @param quizId - The ID of the quiz exercise to be set as visible.
     * @returns A Cypress.Chainable<Cypress.Response<any>> representing the API request response.
     */
    setQuizVisible(quizId: number) {
        return cy.request({
            url: `${QUIZ_EXERCISE_BASE}/${quizId}/set-visible`,
            method: PUT,
        });
    }

    /**
     * Starts a quiz exercise immediately.
     *
     * @param quizId - The ID of the quiz exercise to be started immediately.
     * @returns A Cypress.Chainable<Cypress.Response<any>> representing the API request response.
     */
    startQuizNow(quizId: number) {
        return cy.request({
            url: `${QUIZ_EXERCISE_BASE}/${quizId}/start-now`,
            method: PUT,
        });
    }

    /**
     * Evaluates the quiz exercises in an exam.
     *
     * @param exam - The exam for which to evaluate the quiz exercises.
     * @returns A Cypress.Chainable<Cypress.Response<any>> representing the API request response.
     */
    evaluateExamQuizzes(exam: Exam) {
        return cy.request({
            url: `${COURSE_BASE}/${exam.course!.id}/exams/${exam.id}/student-exams/evaluate-quiz-exercises`,
            method: POST,
        });
    }

    /**
     * Creates a submission for a quiz with only one multiple-choice quiz question.
     *
     * @param quizExercise - The response body of a quiz exercise.
     * @param tickOptions - A list describing which of the 0..n boxes are to be ticked in the submission.
     * @returns A Cypress.Chainable<Cypress.Response<any>> representing the API request response.
     */
    createMultipleChoiceSubmission(quizExercise: any, tickOptions: number[]) {
        const submittedAnswers = [
            {
                ...multipleChoiceSubmissionTemplate.submittedAnswers[0],
                quizQuestion: quizExercise.quizQuestions![0],
                selectedOptions: tickOptions.map((option) => quizExercise.quizQuestions[0].answerOptions[option]),
            },
        ];
        const multipleChoiceSubmission = {
            ...multipleChoiceSubmissionTemplate,
            submittedAnswers,
        };
        return cy.request({
            url: `${EXERCISE_BASE}/${quizExercise.id}/submissions/live`,
            method: POST,
            body: multipleChoiceSubmission,
        });
    }

    /**
     * Creates a submission for a quiz with only one short-answer quiz question.
     *
     * @param quizExercise - The response body of the quiz exercise.
     * @param textAnswers - A list containing the answers to be filled into the gaps. In numerical order.
     * @returns A Cypress.Chainable<Cypress.Response<any>> representing the API request response.
     */
    createShortAnswerSubmission(quizExercise: any, textAnswers: string[]) {
        const submittedTexts = textAnswers.map((answer, index) => {
            return {
                text: answer,
                spot: {
                    id: quizExercise.quizQuestions[0].spots[index].id,
                    spotNr: quizExercise.quizQuestions[0].spots[index].spotNr,
                    width: quizExercise.quizQuestions[0].spots[index].width,
                    invalid: quizExercise.quizQuestions[0].spots[index].invalid,
                },
            };
        });
        const submittedAnswers = [
            {
                ...shortAnswerSubmissionTemplate.submittedAnswers[0],
                quizQuestion: quizExercise.quizQuestions[0],
                submittedTexts,
            },
        ];
        const shortAnswerSubmission = {
            ...shortAnswerSubmissionTemplate,
            submittedAnswers,
        };
        return cy.request({
            url: `${EXERCISE_BASE}/${quizExercise.id}/submissions/live`,
            method: POST,
            body: shortAnswerSubmission,
        });
    }

    /**
     * Gets the participation data for an exercise with the specified exercise ID.
     *
     * @param exerciseId - The ID of the exercise for which to retrieve the participation data.
     * @returns A Cypress.Chainable<Cypress.Response<any>> representing the API request response.
     */
    getExerciseParticipation(exerciseId: number) {
        return cy.request({
            url: `${EXERCISE_BASE}/${exerciseId}/participation`,
            method: GET,
        });
    }

    /**
     * Starts a participation for an exercise with the specified exercise ID.
     *
     * @param exerciseId - The ID of the exercise for which to start the participation.
     * @returns A Cypress.Chainable<Cypress.Response<any>> representing the API request response.
     */
    startExerciseParticipation(exerciseId: number) {
        return cy.request({
            url: `${EXERCISE_BASE}/${exerciseId}/participations`,
            method: POST,
        });
    }

    private updateExercise(exercise: Exercise, type: ExerciseType) {
        let url: string;
        switch (type) {
            case ExerciseType.PROGRAMMING:
                url = PROGRAMMING_EXERCISE_BASE;
                break;
            case ExerciseType.TEXT:
                url = TEXT_EXERCISE_BASE;
                break;
            case ExerciseType.MODELING:
                url = MODELING_EXERCISE_BASE;
                break;
            case ExerciseType.QUIZ:
            default:
                throw new Error(`Exercise type '${type}' is not supported yet!`);
        }
        return cy.request({
            url,
            method: PUT,
            body: exercise,
        });
    }
}
