import { Participation } from 'app/entities/participation/participation.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { Exam } from 'app/entities/exam.model';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import { BASE_API, DELETE, POST, PUT, GET } from '../constants';
import programmingExerciseTemplate from '../../fixtures/requests/programming_exercise_template.json';
import { dayjsToString, generateUUID } from '../utils';
import examTemplate from '../../fixtures/requests/exam_template.json';
import day from 'dayjs/esm';
import { CypressCredentials } from '../users';
import textExerciseTemplate from '../../fixtures/requests/textExercise_template.json';
import fileUploadExerciseTemplate from '../../fixtures/requests/fileUploadExercise_template.json';
import modelingExerciseTemplate from '../../fixtures/requests/modelingExercise_template.json';
import assessment_submission from '../../fixtures/programming_exercise_submissions/assessment/submission.json';
import quizTemplate from '../../fixtures/quiz_exercise_fixtures/quizExercise_template.json';
import multipleChoiceSubmissionTemplate from '../../fixtures/quiz_exercise_fixtures/multipleChoiceSubmission_template.json';
import shortAnswerSubmissionTemplate from '../../fixtures/quiz_exercise_fixtures/shortAnswerSubmission_template.json';
import modelingExerciseSubmissionTemplate from '../../fixtures/exercise/modeling_exercise/modelingSubmission_template.json';
import lectureTemplate from '../../fixtures/lecture/lecture_template.json';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';

export const COURSE_BASE = BASE_API + 'courses/';
export const COURSE_MANAGEMENT_BASE = BASE_API + 'course-management/';
export const EXERCISE_BASE = BASE_API + 'exercises/';
export const PROGRAMMING_EXERCISE_BASE = BASE_API + 'programming-exercises/';
export const QUIZ_EXERCISE_BASE = BASE_API + 'quiz-exercises/';
export const TEXT_EXERCISE_BASE = BASE_API + 'text-exercises/';
export const UPLOAD_EXERCISE_BASE = BASE_API + 'file-upload-exercises/';
export const MODELING_EXERCISE_BASE = BASE_API + 'modeling-exercises';

/**
 * A class which encapsulates all cypress requests related to course management.
 */
export class CourseManagementRequests {
    /**
     * Deletes the course with the specified id.
     * @param courseId the course id
     * @returns <Chainable> request response
     */
    deleteCourse(courseId: number) {
        // Sometimes the server fails with a ConstraintViolationError if we delete the course immediately after a login
        cy.wait(100);
        return cy.request({ method: DELETE, url: COURSE_BASE + courseId });
    }

    /**
     * Creates a course with the specified title and short name.
     * @param customizeGroups whether the predefined groups should be used (so we don't have to wait more than a minute between course and programming exercise creation)
     * @param courseName the title of the course (will generate default name if not provided)
     * @param courseShortName the short name (will generate default name if not provided)
     * @param start the start date of the course (default: now() - 2 hours)
     * @param end the end date of the course (default: now() + 2 hours)
     * @returns <Chainable> request response
     */
    createCourse(
        customizeGroups = false,
        courseName = 'Cypress course' + generateUUID(),
        courseShortName = 'cypress' + generateUUID(),
        start = day().subtract(2, 'hours'),
        end = day().add(2, 'hours'),
    ): Cypress.Chainable<Cypress.Response<Course>> {
        const course = new Course();
        course.title = courseName;
        course.shortName = courseShortName;
        course.testCourse = true;
        course.startDate = start;
        course.endDate = end;

        const allowGroupCustomization: boolean = Cypress.env('allowGroupCustomization');
        if (customizeGroups && allowGroupCustomization) {
            course.studentGroupName = Cypress.env('studentGroupName');
            course.teachingAssistantGroupName = Cypress.env('tutorGroupName');
            course.editorGroupName = Cypress.env('editorGroupName');
            course.instructorGroupName = Cypress.env('instructorGroupName');
        }
        return cy.request({
            url: BASE_API + 'courses',
            method: POST,
            body: course,
        });
    }

    /**
     * Creates a programming exercise with the specified title and other data.
     * @param body an object containing either the course or exercise group the exercise will be added to
     * @param scaMaxPenalty? the max percentage (0-100) static code analysis can reduce from the points (if sca should be disabled pass null)
     * @param recordTestwiseCoverage enable testwise coverage analysis for this exercise (default is false)
     * @param releaseDate when the programming exercise should be available (default is now)
     * @param dueDate when the programming exercise should be due (default is now + 1 day)
     * @param title the title of the programming exercise
     * @param programmingShortName the short name of the programming exercise
     * @param packageName the package name of the programming exercise
     * @param assessmentDate the due date of the assessment
     * @param assessmentType the assessment type of the exercise (default is AUTOMATIC)
     * @returns <Chainable> request response
     */
    createProgrammingExercise(
        body: { course: Course } | { exerciseGroup: ExerciseGroup },
        scaMaxPenalty?: number,
        recordTestwiseCoverage = false,
        releaseDate = day(),
        dueDate = day().add(1, 'day'),
        title = 'Cypress programming exercise ' + generateUUID(),
        programmingShortName = 'cypress' + generateUUID(),
        packageName = 'de.test',
        assessmentDate = day().add(2, 'days'),
        assessmentType = CypressAssessmentType.AUTOMATIC,
    ): Cypress.Chainable<Cypress.Response<ProgrammingExercise>> {
        const template = {
            ...programmingExerciseTemplate,
            title,
            shortName: programmingShortName,
            packageName,
            assessmentType: CypressAssessmentType[assessmentType],
        };
        const exercise: ProgrammingExercise = Object.assign({}, template, body) as ProgrammingExercise;
        const isExamExercise = body.hasOwnProperty('exerciseGroup');
        if (!isExamExercise) {
            exercise.releaseDate = releaseDate;
            exercise.dueDate = dueDate;
            exercise.assessmentDueDate = assessmentDate;
        }

        if (scaMaxPenalty) {
            exercise.staticCodeAnalysisEnabled = true;
            exercise.maxStaticCodeAnalysisPenalty = scaMaxPenalty;
        }

        exercise.testwiseCoverageEnabled = recordTestwiseCoverage;

        return cy.request({
            url: PROGRAMMING_EXERCISE_BASE + 'setup',
            method: POST,
            body: exercise,
        });
    }

    /**
     * Submits the example submission to the specified repository.
     * @param repositoryId the repository id. The repository id is equal to the participation id.
     * @returns <Chainable> request
     */
    makeProgrammingExerciseSubmission(repositoryId: number) {
        // TODO: For now it is enough to submit the one prepared json file, but in the future this method should support different package names and submissions.
        return cy.request({
            url: `${BASE_API}repository/${repositoryId}/files?commit=yes`,
            method: PUT,
            body: assessment_submission,
        });
    }

    updateModelingExerciseDueDate(exercise: ModelingExercise, due = day()) {
        exercise.dueDate = due;
        return this.updateExercise(exercise, CypressExerciseType.MODELING);
    }

    private updateExercise(exercise: Exercise, type: CypressExerciseType) {
        let url: string;
        switch (type) {
            case CypressExerciseType.PROGRAMMING:
                url = PROGRAMMING_EXERCISE_BASE;
                break;
            case CypressExerciseType.TEXT:
                url = TEXT_EXERCISE_BASE;
                break;
            case CypressExerciseType.MODELING:
                url = MODELING_EXERCISE_BASE;
                break;
            case CypressExerciseType.QUIZ:
            default:
                throw new Error(`Exercise type '${type}' is not supported yet!`);
        }
        return cy.request({
            url,
            method: PUT,
            body: exercise,
        });
    }

    /**
     * Adds the specified student to the course.
     * @param course the course
     * @param student the student
     * @returns <Chainable> request response
     */
    addStudentToCourse(course: Course, user: CypressCredentials) {
        return this.addUserToCourse(course.id!, user.username, 'students');
    }

    /**
     * Adds the specified user to the tutor group in the course
     */
    addTutorToCourse(course: Course, user: CypressCredentials) {
        return this.addUserToCourse(course.id!, user.username, 'tutors');
    }

    /**
     * Adds the specified user to the instructor group in the course
     */
    addInstructorToCourse(course: Course, user: CypressCredentials) {
        return this.addUserToCourse(course.id!, user.username, 'instructors');
    }

    private addUserToCourse(courseId: number, username: string, roleIdentifier: string) {
        return cy.request({ method: POST, url: `${COURSE_BASE}${courseId}/${roleIdentifier}/${username}` });
    }

    /**
     * Creates an exam with the provided settings.
     * @param exam the exam object created by a {@link CypressExamBuilder}
     * @returns <Chainable> request response
     */
    createExam(exam: Exam) {
        return cy.request({ url: COURSE_BASE + exam.course!.id + '/exams', method: POST, body: exam });
    }

    /**
     * Deletes the exam with the given parameters
     * @returns <Chainable> request response
     * */
    deleteExam(exam: Exam) {
        return cy.request({ method: DELETE, url: COURSE_BASE + exam.course!.id + '/exams/' + exam.id });
    }

    /**
     * register the student for the exam
     * @returns <Chainable> request response
     */
    registerStudentForExam(exam: Exam, student: CypressCredentials) {
        return cy.request({ method: POST, url: COURSE_BASE + exam.course!.id + '/exams/' + exam.id + '/students/' + student.username });
    }

    /**
     * add exercise group to exam
     * @returns <Chainable> request response
     * */
    addExerciseGroupForExam(exam: Exam, title = 'group' + generateUUID(), mandatory = true) {
        const exerciseGroup = new ExerciseGroup();
        exerciseGroup.exam = exam;
        exerciseGroup.title = title;
        exerciseGroup.isMandatory = mandatory;
        return cy.request({ method: POST, url: COURSE_BASE + exam.course!.id + '/exams/' + exam.id + '/exerciseGroups', body: exerciseGroup });
    }

    /**
     * add text exercise to an exercise group in exam or to a course
     * @param body an object containing either the course or exercise group the exercise will be added to
     * @param title the title for the exercise
     * @returns <Chainable> request response
     */
    createTextExercise(body: { course: Course } | { exerciseGroup: ExerciseGroup }, title = 'Text exercise ' + generateUUID()) {
        const template = { ...textExerciseTemplate, title };
        const textExercise = Object.assign({}, template, body);
        return cy.request({ method: POST, url: TEXT_EXERCISE_BASE, body: textExercise });
    }

    /**
     * generate all missing individual exams
     * @returns <Chainable> request response
     */
    generateMissingIndividualExams(exam: Exam) {
        return cy.request({ method: POST, url: COURSE_BASE + exam.course!.id + '/exams/' + exam.id + '/generate-missing-student-exams' });
    }

    /**
     * Prepares individual exercises for exam start
     * @returns <Chainable> request response
     */
    prepareExerciseStartForExam(exam: Exam) {
        return cy.request({ method: POST, url: COURSE_BASE + exam.course!.id + '/exams/' + exam.id + '/student-exams/start-exercises' });
    }

    /**
     * Creates a modeling exercise
     * @param body an object containing either the course or exercise group the exercise will be added to
     * @param title the title for the exercise
     * @param releaseDate time of release for the exercise
     * @param dueDate when the modeling exercise should be due (default is now + 1 day)
     * @param assessmentDueDate the due date of the assessment
     * @returns <Chainable> request response
     */
    createModelingExercise(
        body: { course: Course } | { exerciseGroup: ExerciseGroup },
        title = 'Cypress modeling exercise ' + generateUUID(),
        releaseDate = day(),
        dueDate = day().add(1, 'days'),
        assessmentDueDate = day().add(2, 'days'),
    ): Cypress.Chainable<Cypress.Response<ModelingExercise>> {
        const templateCopy = {
            ...modelingExerciseTemplate,
            title,
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
        return cy.request({
            url: MODELING_EXERCISE_BASE,
            method: POST,
            body: newModelingExercise,
        });
    }

    updateModelingExerciseAssessmentDueDate(exercise: ModelingExercise, due = day()) {
        exercise.assessmentDueDate = due;
        return this.updateExercise(exercise, CypressExerciseType.MODELING);
    }

    deleteModelingExercise(exerciseID: number) {
        return cy.request({
            url: `${MODELING_EXERCISE_BASE}/${exerciseID}`,
            method: DELETE,
        });
    }

    makeModelingExerciseSubmission(exerciseID: number, participation: Participation) {
        return cy.request({
            url: `${EXERCISE_BASE}${exerciseID}/modeling-submissions`,
            method: PUT,
            body: {
                ...modelingExerciseSubmissionTemplate,
                id: participation.submissions![0].id,
                participation,
            },
        });
    }

    deleteQuizExercise(exerciseId: number) {
        return cy.request({
            url: QUIZ_EXERCISE_BASE + exerciseId,
            method: DELETE,
        });
    }

    /**
     * Creates a quiz exercise
     * @param body an object containing either the course or exercise group the exercise will be added to
     * @param quizQuestions list of quizQuestion objects that make up the Quiz. Can be multiple choice, short answer or drag and drop quizzes.
     * @param title the title for the Quiz
     * @param releaseDate time of release for the quiz
     * @param duration the duration in seconds that the student gets to complete the quiz
     * @returns <Chainable> request response
     */
    createQuizExercise(
        body: { course: Course } | { exerciseGroup: ExerciseGroup },
        quizQuestions: [any],
        title = 'Cypress quiz exercise' + generateUUID(),
        releaseDate = day().add(1, 'year'),
        duration = 600,
    ) {
        const quizExercise: any = {
            ...quizTemplate,
            title,
            quizQuestions,
            duration,
        };
        let newQuizExercise;
        const dates = {
            releaseDate: dayjsToString(releaseDate),
        };
        if (body.hasOwnProperty('course')) {
            newQuizExercise = Object.assign({}, quizExercise, dates, body);
        } else {
            newQuizExercise = Object.assign({}, quizExercise, body);
        }
        return cy.request({
            url: QUIZ_EXERCISE_BASE,
            method: POST,
            body: newQuizExercise,
        });
    }

    setQuizVisible(quizId: number) {
        return cy.request({
            url: `${QUIZ_EXERCISE_BASE}${quizId}/set-visible`,
            method: PUT,
        });
    }

    startQuizNow(quizId: number) {
        return cy.request({
            url: `${QUIZ_EXERCISE_BASE}${quizId}/start-now`,
            method: PUT,
        });
    }

    evaluateExamQuizzes(exam: Exam) {
        return cy.request({
            url: `${COURSE_BASE}${exam.course!.id}/exams/${exam.id}/student-exams/evaluate-quiz-exercises`,
            method: POST,
        });
    }

    /**
     * Creates a file upload exercise
     * @param body an object containing either the course or exercise group the exercise will be added to
     * @param title the title for the exercise
     * @returns <Chainable> request response
     */
    createFileUploadExercise(
        body: { course: Course } | { exerciseGroup: ExerciseGroup },
        title = 'Upload exercise ' + generateUUID(),
    ): Cypress.Chainable<Cypress.Response<FileUploadExercise>> {
        const template = { ...fileUploadExerciseTemplate, title };
        const uploadExercise = Object.assign({}, template, body);
        return cy.request({ method: POST, url: UPLOAD_EXERCISE_BASE, body: uploadExercise });
    }

    deleteFileUploadExercise(exerciseID: number) {
        return cy.request({
            url: `${UPLOAD_EXERCISE_BASE}/${exerciseID}`,
            method: DELETE,
        });
    }

    makeFileUploadExerciseSubmission(exerciseId: number, file: string) {
        return cy.request({
            url: `${EXERCISE_BASE}${exerciseId}/file-upload-submissions`,
            method: POST,
            body: { submissionExerciseType: 'file-upload', file, id: null },
        });
    }

    updateFileUploadExerciseDueDate(exercise: FileUploadExercise, due = day()) {
        exercise.dueDate = due;
        return this.updateExercise(exercise, CypressExerciseType.UPLOAD);
    }

    updateFileUploadExerciseAssessmentDueDate(exercise: FileUploadExercise, due = day()) {
        exercise.assessmentDueDate = due;
        return this.updateExercise(exercise, CypressExerciseType.UPLOAD);
    }

    makeTextExerciseSubmission(exerciseId: number, text: string) {
        return cy.request({
            url: `${EXERCISE_BASE}${exerciseId}/text-submissions`,
            method: PUT,
            body: { submissionExerciseType: 'text', text, id: null },
        });
    }

    /**
     * Creates a submission for a Quiz with only one multiple-choice quiz question
     * @param quizExercise the response body of a quiz exercise
     * @param tickOptions a list describing which of the 0..n boxes are to be ticked in the submission
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
            url: EXERCISE_BASE + quizExercise.id + '/submissions/live',
            method: POST,
            body: multipleChoiceSubmission,
        });
    }

    /**
     * Creates a submission for a Quiz with only one short-answer quiz question
     * @param quizExercise the response body of the quiz exercise
     * @param textAnswers a list containing the answers to be filled into the gaps. In numerical order.
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
            url: EXERCISE_BASE + quizExercise.id + '/submissions/live',
            method: POST,
            body: shortAnswerSubmission,
        });
    }

    getExerciseParticipation(exerciseId: number) {
        return cy.request({
            url: EXERCISE_BASE + exerciseId + '/participation',
            method: GET,
        });
    }

    startExerciseParticipation(exerciseId: number) {
        return cy.request({
            url: EXERCISE_BASE + exerciseId + '/participations',
            method: POST,
        });
    }

    updateTextExerciseDueDate(exercise: TextExercise, due = day()) {
        exercise.dueDate = due;
        return this.updateExercise(exercise, CypressExerciseType.TEXT);
    }

    updateTextExerciseAssessmentDueDate(exercise: TextExercise, due = day()) {
        exercise.assessmentDueDate = due;
        return this.updateExercise(exercise, CypressExerciseType.TEXT);
    }

    deleteLecture(lectureId: number) {
        return cy.request({
            url: `${BASE_API}lectures/${lectureId}`,
            method: DELETE,
        });
    }

    createLecture(course: Course, title = 'Cypress lecture' + generateUUID(), startDate = day(), endDate = day().add(10, 'minutes')) {
        const lecture = {
            ...lectureTemplate,
            course,
            title,
            startDate,
            endDate,
        };
        return cy.request({
            url: `${BASE_API}lectures`,
            method: POST,
            body: lecture,
        });
    }
}

/**
 * Helper class to construct exam objects for the {@link CourseManagementRequests.createExam} method.
 */
export class CypressExamBuilder {
    readonly template: any = examTemplate;

    /**
     * Initializes the exam builder.
     * @param course the course dto of a previous createCourse request
     */
    constructor(course: any) {
        this.template.course = course;
        this.template.title = 'exam' + generateUUID();
        this.template.visibleDate = dayjsToString(day());
        this.template.startDate = dayjsToString(day().add(1, 'day'));
        this.template.endDate = dayjsToString(day().add(2, 'day'));
        this.template.workingTime = 86400;
    }

    /**
     * @param title the title of the exam
     */
    title(title: string) {
        this.template.title = title;
        return this;
    }

    /**
     * @param randomize if the exercise order should be randomized
     */
    randomizeOrder(randomize: boolean) {
        this.template.randomizeExerciseOrder = randomize;
        return this;
    }

    /**
     * @param rounds how many correction rounds there are for this exam (default is 1)
     */
    correctionRounds(rounds: number) {
        this.template.numberOfCorrectionRoundsInExam = rounds;
        return this;
    }

    /**
     * @param points the maximum amount of points achieved in the exam (default is 10)
     */
    maxPoints(points: number) {
        this.template.maxPoints = points;
        return this;
    }

    /**
     * @param period the grace period in seconds for this exam (default is 30)
     */
    gracePeriod(period: number) {
        this.template.gracePeriod = period;
        return this;
    }

    /**
     * @param amount the amount of exercises in this exam
     */
    numberOfExercises(amount: number) {
        this.template.numberOfExercisesInExam = amount;
        return this;
    }

    /**
     * @param date the date when the exam should be visible
     */
    visibleDate(date: day.Dayjs) {
        this.template.visibleDate = dayjsToString(date);
        return this;
    }

    /**
     *
     * @param date the date when the exam should start
     */
    startDate(date: day.Dayjs) {
        this.template.startDate = dayjsToString(date);
        return this;
    }

    /**
     *
     * @param date the date when the exam should end
     */
    endDate(date: day.Dayjs) {
        this.template.endDate = dayjsToString(date);
        return this;
    }

    publishResultsDate(date: day.Dayjs) {
        this.template.publishResultsDate = dayjsToString(date);
        return this;
    }

    examStudentReviewStart(date: day.Dayjs) {
        this.template.examStudentReviewStart = dayjsToString(date);
        return this;
    }

    examStudentReviewEnd(date: day.Dayjs) {
        this.template.examStudentReviewEnd = dayjsToString(date);
        return this;
    }

    startText(text: string) {
        this.template.startText = text;
        return this;
    }

    endText(text: string) {
        this.template.endText = text;
        return this;
    }

    confirmationStartText(text: string) {
        this.template.confirmationStartText = text;
        return this;
    }

    confirmationEndText(text: string) {
        this.template.confirmationEndText = text;
        return this;
    }

    /**
     * @returns the exam object
     */
    build() {
        return this.template;
    }
}

export enum CypressAssessmentType {
    AUTOMATIC,
    SEMI_AUTOMATIC,
    MANUAL,
}

export enum CypressExerciseType {
    PROGRAMMING,
    MODELING,
    TEXT,
    QUIZ,
    UPLOAD,
}
