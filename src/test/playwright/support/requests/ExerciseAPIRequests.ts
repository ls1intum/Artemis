import dayjs from 'dayjs';
import { Page } from 'playwright-core';

import { Course } from 'app/entities/course.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { TextExercise } from 'app/entities/text/text-exercise.model';

import fileUploadExerciseTemplate from '../../fixtures/exercise/file-upload/template.json';
import modelingExerciseSubmissionTemplate from '../../fixtures/exercise/modeling/submission.json';
import modelingExerciseTemplate from '../../fixtures/exercise/modeling/template.json';
import cProgrammingExerciseTemplate from '../../fixtures/exercise/programming/c/template.json';
import javaProgrammingExerciseTemplate from '../../fixtures/exercise/programming/java/template.json';
import pythonProgrammingExerciseTemplate from '../../fixtures/exercise/programming/python/template.json';
import multipleChoiceSubmissionTemplate from '../../fixtures/exercise/quiz/multiple_choice/submission.json';
import shortAnswerSubmissionTemplate from '../../fixtures/exercise/quiz/short_answer/submission.json';
import quizTemplate from '../../fixtures/exercise/quiz/template.json';
import textExerciseTemplate from '../../fixtures/exercise/text/template.json';
import {
    BASE_API,
    COURSE_BASE,
    EXERCISE_BASE,
    Exercise,
    ExerciseMode,
    ExerciseType,
    MODELING_EXERCISE_BASE,
    PROGRAMMING_EXERCISE_BASE,
    ProgrammingExerciseAssessmentType,
    ProgrammingLanguage,
    QUIZ_EXERCISE_BASE,
    QuizMode,
    TEXT_EXERCISE_BASE,
    UPLOAD_EXERCISE_BASE,
} from '../constants';
import { dayjsToString, generateUUID, titleLowercase } from '../utils';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { Participation } from 'app/entities/participation/participation.model';
import { Exam } from 'app/entities/exam/exam.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Team } from 'app/entities/team.model';
import { TeamAssignmentConfig } from 'app/entities/team-assignment-config.model';
import { ProgrammingExerciseSubmission } from '../pageobjects/exercises/programming/OnlineEditorPage';
import { Fixtures } from '../../fixtures/fixtures';
import { ProgrammingExerciseTestCase, Visibility } from 'app/entities/programming/programming-exercise-test-case.model';

type PatchProgrammingExerciseTestVisibilityDto = {
    id: number;
    weight: number;
    bonusPoints: number;
    bonusMultiplier: number;
    visibility: Visibility;
}[];

const MAX_RETRIES: number = 20;
const RETRY_DELAY: number = 3000;

export class ExerciseAPIRequests {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

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
     * @returns Promise<ProgrammingExercise> representing the programming exercise created.
     */
    async createProgrammingExercise(options: {
        course?: Course;
        exerciseGroup?: ExerciseGroup;
        scaMaxPenalty?: number | undefined;
        recordTestwiseCoverage?: boolean;
        releaseDate?: dayjs.Dayjs;
        dueDate?: dayjs.Dayjs;
        title?: string;
        programmingShortName?: string;
        programmingLanguage?: ProgrammingLanguage;
        packageName?: string;
        assessmentDate?: dayjs.Dayjs;
        assessmentType?: ProgrammingExerciseAssessmentType;
        mode?: ExerciseMode;
        teamAssignmentConfig?: TeamAssignmentConfig;
    }): Promise<ProgrammingExercise> {
        const {
            course,
            exerciseGroup,
            scaMaxPenalty = undefined,
            recordTestwiseCoverage = false,
            releaseDate = dayjs(),
            dueDate = dayjs().add(1, 'day'),
            title = 'Programming ' + generateUUID(),
            programmingShortName = 'programming' + generateUUID(),
            programmingLanguage = ProgrammingLanguage.JAVA,
            packageName = 'de.test',
            assessmentDate = dayjs().add(2, 'days'),
            assessmentType = ProgrammingExerciseAssessmentType.AUTOMATIC,
            mode = ExerciseMode.INDIVIDUAL,
            teamAssignmentConfig,
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
        exercise.buildConfig!.testwiseCoverageEnabled = recordTestwiseCoverage;
        exercise.mode = mode;
        exercise.teamAssignmentConfig = teamAssignmentConfig;

        const response = await this.page.request.post(`${PROGRAMMING_EXERCISE_BASE}/setup`, { data: exercise });
        return response.json();
    }

    /**
     * Retrieves the test cases for passed exercise and adjusts their visibility according.
     * <br>
     * Note: test cases are not available before the tests of the solution have completely run through
     *       -> we need to do retries until the tests have been executed
     *
     * @param programmingExercise for which the test cases shall be set to {@link newVisibility}
     * @param newVisibility that is applied for all found test cases
     * @param retryNumber
     */
    async changeProgrammingExerciseTestVisibility(programmingExercise: ProgrammingExercise, newVisibility: Visibility, retryNumber: number) {
        if (retryNumber >= MAX_RETRIES) {
            throw new Error('Could not find test cases (tests for solution might not be finished yet)');
        }

        const response = await this.page.request.get(`${PROGRAMMING_EXERCISE_BASE}/${programmingExercise.id}/test-cases`);
        const testCases = (await response.json()) as unknown as ProgrammingExerciseTestCase[];

        if (retryNumber > 0) {
            console.log(`Could not find test cases yet, retrying... (${retryNumber} / ${MAX_RETRIES})`);
        }

        await this.page.waitForTimeout(RETRY_DELAY);

        if (testCases.length > 0) {
            await this.updateProgrammingExerciseTestCaseVisibility(programmingExercise.id!, testCases, newVisibility);
        } else {
            await this.changeProgrammingExerciseTestVisibility(programmingExercise, newVisibility, retryNumber + 1);
        }
    }

    /**
     * Submits the example submission to the specified repository.
     *
     * @param repositoryId - The repository ID. The repository ID is equal to the participation ID.
     * @param submission - The example submission to be submitted.
     */
    async makeProgrammingExerciseSubmission(repositoryId: number, submission: ProgrammingExerciseSubmission) {
        const data = await Promise.all(
            submission.files.map(async (file) => {
                let fileName = file.name;
                if (submission.packageName) {
                    fileName = `src/${submission.packageName.replace(/\./g, '/')}/${file.name}`;
                }
                return {
                    fileName,
                    fileContent: await Fixtures.get(file.path),
                };
            }),
        );
        await this.page.request.put(`${BASE_API}/repository/${repositoryId}/files?commit=yes`, { data });
    }

    async createProgrammingExerciseFile(repositoryId: number, filename: string) {
        return await this.page.request.post(`${BASE_API}/repository/${repositoryId}/file?file=${filename}`);
    }

    /**
     * Adds a text exercise to an exercise group in an exam or to a course.
     *
     * @param body - An object containing either the course or exercise group the exercise will be added to.
     * @param title - The title for the text exercise (optional, default: auto-generated).
     */
    async createTextExercise(body: { course: Course } | { exerciseGroup: ExerciseGroup }, title = 'Text ' + generateUUID()): Promise<TextExercise> {
        const template = {
            ...textExerciseTemplate,
            title,
            channelName: 'exercise-' + titleLowercase(title),
        };
        const textExercise = Object.assign({}, template, body);
        const response = await this.page.request.post(TEXT_EXERCISE_BASE, { data: textExercise });
        return response.json();
    }

    /**
     * Deletes a text exercise with the specified exercise ID.
     *
     * @param exerciseId - The ID of the text exercise to be deleted.
     */
    async deleteTextExercise(exerciseId: number) {
        await this.page.request.delete(`${TEXT_EXERCISE_BASE}/${exerciseId}`);
    }

    /**
     * Makes a text exercise submission for the specified exercise ID with the given text content.
     *
     * @param exerciseId - The ID of the text exercise for which the submission is made.
     * @param text - The text content of the submission.
     */
    async makeTextExerciseSubmission(exerciseId: number, text: string, createNewSubmission = true) {
        const url = `${EXERCISE_BASE}/${exerciseId}/text-submissions`;
        const data = { submissionExerciseType: 'text', text };

        if (createNewSubmission) {
            await this.page.request.post(url, { data });
        } else {
            await this.page.request.put(url, { data });
        }
    }

    /**
     * Creates a file upload exercise.
     *
     * @param body - An object containing either the course or exercise group the exercise will be added to.
     * @param title - The title for the exercise (optional, default: auto-generated).
     * @returns A Promise<FileUploadExercise> representing the file upload exercise created.
     */
    async createFileUploadExercise(body: { course: Course } | { exerciseGroup: ExerciseGroup }, title = 'Upload ' + generateUUID()): Promise<FileUploadExercise> {
        const template = {
            ...fileUploadExerciseTemplate,
            title,
            channelName: 'exercise-' + titleLowercase(title),
        };
        const uploadExercise = Object.assign({}, template, body);
        const response = await this.page.request.post(UPLOAD_EXERCISE_BASE, { data: uploadExercise });
        return response.json();
    }

    /**
     * Deletes a file upload exercise with the specified exercise ID.
     *
     * @param exerciseID - The ID of the file upload exercise to be deleted.
     */
    async deleteFileUploadExercise(exerciseID: number) {
        await this.page.request.delete(`${UPLOAD_EXERCISE_BASE}/${exerciseID}`);
    }

    /**
     * Makes a file upload exercise submission for the specified exercise ID with the given file.
     *
     * @param exerciseId - The ID of the file upload exercise for which the submission is made.
     * @param file - The file content of the submission.
     */
    async makeFileUploadExerciseSubmission(exerciseId: number, file: string) {
        await this.page.request.post(`${EXERCISE_BASE}/${exerciseId}/file-upload-submissions`, {
            data: { submissionExerciseType: 'file-upload', file },
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
     * @returns A Promise<ModelingExercise> representing the modeling exercise created.
     */
    async createModelingExercise(
        body: { course: Course } | { exerciseGroup: ExerciseGroup },
        title = 'Modeling ' + generateUUID(),
        releaseDate = dayjs(),
        dueDate = dayjs().add(1, 'days'),
        assessmentDueDate = dayjs().add(2, 'days'),
    ): Promise<ModelingExercise> {
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
        if (body.hasOwnProperty('course')) {
            newModelingExercise = Object.assign({}, templateCopy, dates, body);
        } else {
            newModelingExercise = Object.assign({}, templateCopy, body);
        }
        const response = await this.page.request.post(MODELING_EXERCISE_BASE, { data: newModelingExercise });
        return response.json();
    }

    /**
     * Updates the assessment due date of a modeling exercise.
     *
     * @param exercise - The modeling exercise to update.
     * @param due - The new assessment due date (optional, default: current date).
     */
    async updateModelingExerciseAssessmentDueDate(exercise: ModelingExercise, due = dayjs()) {
        exercise.assessmentDueDate = due;
        return await this.updateExercise(exercise, ExerciseType.MODELING);
    }

    /**
     * Deletes a modeling exercise with the specified exercise ID.
     *
     * @param exerciseID - The ID of the modeling exercise to be deleted.
     */
    async deleteModelingExercise(exerciseID: number) {
        return this.page.request.delete(`${MODELING_EXERCISE_BASE}/${exerciseID}`);
    }

    /**
     * Makes a modeling exercise submission for the specified exercise ID and participation.
     *
     * @param exerciseID - The ID of the modeling exercise for which the submission is made.
     * @param participation - The participation data for the submission.
     */
    async makeModelingExerciseSubmission(exerciseID: number, participation: Participation) {
        return this.page.request.put(`${EXERCISE_BASE}/${exerciseID}/modeling-submissions`, {
            data: {
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
     */
    async updateModelingExerciseDueDate(exercise: ModelingExercise, due = dayjs()) {
        exercise.dueDate = due;
        await this.updateExercise(exercise, ExerciseType.MODELING);
    }

    /**
     * Creates a quiz exercise.
     * @param options An object containing the options for creating the programming exercise
     * - body - An object containing either the course or exercise group the exercise will be added to.
     * - quizQuestions - A list of quiz question objects that make up the quiz (e.g., multiple choice, short answer, or drag and drop).
     * - title - The title for the quiz exercise (optional, default: auto-generated).
     * - releaseDate - The release date of the quiz exercise (optional, default: current date + 1 year).
     * - startOfWorkingTime - The start of working time of the quiz exercise (optional, default: none).
     * - duration - The duration in seconds that students get to complete the quiz (optional, default: 600 seconds).
     * - quizMode - The mode of the quiz exercise (optional, default: synchronized).
     * @returns Promise<QuizExercise> representing the quiz exercise created.
     */
    async createQuizExercise(options: {
        body: { course: Course } | { exerciseGroup: ExerciseGroup };
        quizQuestions: any[];
        title?: string;
        releaseDate?: dayjs.Dayjs;
        startOfWorkingTime?: dayjs.Dayjs;
        duration?: number;
        quizMode?: QuizMode;
    }): Promise<QuizExercise> {
        const {
            body,
            quizQuestions,
            title = 'Quiz ' + generateUUID(),
            releaseDate = dayjs().add(1, 'year'),
            startOfWorkingTime,
            duration = 600,
            quizMode = QuizMode.SYNCHRONIZED,
        } = options;

        const quizExercise: any = {
            ...quizTemplate,
            title,
            quizQuestions,
            duration,
            quizMode,
            channelName: 'exercise-' + titleLowercase(title),
        };
        const dates = {
            releaseDate: dayjsToString(releaseDate),
        };
        const quizBatches = [];
        if (startOfWorkingTime) {
            quizBatches.push({ startTime: dayjsToString(startOfWorkingTime) });
        }

        const newQuizExercise = body.hasOwnProperty('course') ? { ...quizExercise, quizBatches, ...dates, ...body } : { ...quizExercise, ...body };
        const multipartData = {
            exercise: {
                name: 'exercise',
                mimeType: 'application/json',
                buffer: Buffer.from(JSON.stringify(newQuizExercise)),
            },
        };

        const response = await this.page.request.post(QUIZ_EXERCISE_BASE, {
            multipart: multipartData,
        });
        return response.json();
    }

    /**
     * Deletes a quiz exercise with the specified exercise ID.
     *
     * @param exerciseId - The ID of the quiz exercise to be deleted.
     */
    async deleteQuizExercise(exerciseId: number) {
        await this.page.request.delete(`${QUIZ_EXERCISE_BASE}/${exerciseId}`);
    }

    /**
     * Sets a quiz exercise as visible.
     *
     * @param quizId - The ID of the quiz exercise to be set as visible.
     */
    async setQuizVisible(quizId: number) {
        await this.page.request.put(`${QUIZ_EXERCISE_BASE}/${quizId}/set-visible`);
    }

    /**
     * Starts a quiz exercise immediately.
     *
     * @param quizId - The ID of the quiz exercise to be started immediately.
     */
    async startQuizNow(quizId: number) {
        await this.page.request.put(`${QUIZ_EXERCISE_BASE}/${quizId}/start-now`);
    }

    /**
     * Evaluates the quiz exercises in an exam.
     *
     * @param exam - The exam for which to evaluate the quiz exercises.
     */
    async evaluateExamQuizzes(exam: Exam) {
        await this.page.request.post(`${COURSE_BASE}/${exam.course!.id}/exams/${exam.id}/student-exams/evaluate-quiz-exercises`);
    }

    /**
     * Creates a submission for a quiz with only one multiple-choice quiz question.
     *
     * @param quizExercise - The response body of a quiz exercise.
     * @param tickOptions - A list describing which of the 0..n boxes are to be ticked in the submission.
     */
    async createMultipleChoiceSubmission(quizExercise: any, tickOptions: number[]) {
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
        await this.page.request.post(`${EXERCISE_BASE}/${quizExercise.id}/submissions/live?submit=true`, { data: multipleChoiceSubmission });
    }

    /**
     * Creates a submission for a quiz with only one short-answer quiz question.
     *
     * @param quizExercise - The response body of the quiz exercise.
     * @param textAnswers - A list containing the answers to be filled into the gaps. In numerical order.
     */
    async createShortAnswerSubmission(quizExercise: any, textAnswers: string[]) {
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
        await this.page.request.post(`${EXERCISE_BASE}/${quizExercise.id}/submissions/live?submit=true`, { data: shortAnswerSubmission });
    }

    /**
     * Gets the participation data for an exercise with the specified exercise ID.
     *
     * @param exerciseId - The ID of the exercise for which to retrieve the participation data.
     * @returns A Promise<StudentParticipation> representing the student participation.
     */
    async getExerciseParticipation(exerciseId: number): Promise<StudentParticipation> {
        const response = await this.page.request.get(`${EXERCISE_BASE}/${exerciseId}/participation`);
        return response.json();
    }

    /**
     * Starts a participation for an exercise with the specified exercise ID.
     *
     * @param exerciseId - The ID of the exercise for which to start the participation.
     * @returns APIResponse representing the API request response.
     */
    async startExerciseParticipation(exerciseId: number) {
        return await this.page.request.post(`${EXERCISE_BASE}/${exerciseId}/participations`);
    }

    private async updateProgrammingExerciseTestCaseVisibility(
        programmingExerciseId: number,
        programmingExerciseTestCases: ProgrammingExerciseTestCase[],
        newVisibility: Visibility,
    ) {
        const updatedTestCaseSettings: PatchProgrammingExerciseTestVisibilityDto = [];

        for (const testCase of programmingExerciseTestCases) {
            updatedTestCaseSettings.push({
                id: testCase.id!,
                weight: testCase.weight!,
                bonusPoints: testCase.bonusPoints!,
                bonusMultiplier: testCase.bonusMultiplier!,
                visibility: newVisibility,
            });
        }

        return await this.page.request.patch(`${PROGRAMMING_EXERCISE_BASE}/${programmingExerciseId}/update-test-cases`, { data: updatedTestCaseSettings });
    }

    private async updateExercise(exercise: Exercise, type: ExerciseType) {
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
        return await this.page.request.put(url, { data: exercise });
    }

    /**
     * Creates a team for a team-based exercise.
     *
     * @param exerciseId - The ID of the exercise for which to create a team.
     * @param students - A list of student users to be added to the team.
     * @param tutor - The tutor user who is the owner of the team.
     * @returns A Promise<Team> representing the team created.
     */
    async createTeam(exerciseId: number, students: any[], tutor: any) {
        const teamId = generateUUID();
        const team: Team = {
            name: `Team ${teamId}`,
            shortName: `team${teamId}`,
            students,
            owner: tutor,
        };
        return await this.page.request.post(`${EXERCISE_BASE}/${exerciseId}/teams`, { data: team });
    }
}
