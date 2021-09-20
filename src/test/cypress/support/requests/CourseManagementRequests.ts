import { BASE_API, DELETE, POST, PUT, EXERCISE_BASE, GET } from '../constants';
import courseTemplate from '../../fixtures/requests/course.json';
import programmingExerciseTemplate from '../../fixtures/requests/programming_exercise_template.json';
import { dayjsToString, generateUUID } from '../utils';
import examTemplate from '../../fixtures/requests/exam_template.json';
import day from 'dayjs';
import { CypressCredentials } from '../users';
import textExerciseTemplate from '../../fixtures/requests/textExercise_template.json';
import modelingExerciseTemplate from '../../fixtures/requests/modelingExercise_template.json';
import exerciseGroup from '../../fixtures/requests/exerciseGroup_template.json';
import quizTemplate from '../../fixtures/quiz_exercise_fixtures/quizExercise_template.json';
import multipleChoiceSubmissionTemplate from '../../fixtures/quiz_exercise_fixtures/multipleChoiceSubmission_template.json';
import shortAnswerSubmissionTemplate from '../../fixtures/quiz_exercise_fixtures/shortAnswerSubmission_template.json';
export const COURSE_BASE = BASE_API + 'courses/';
export const COURSE_MANAGEMENT_BASE = BASE_API + 'course-management/';
export const EXERCISE_BASE = BASE_API + 'exercises/';
export const PROGRAMMING_EXERCISE_BASE = BASE_API + 'programming-exercises/';
export const QUIZ_EXERCISE_BASE = BASE_API + 'quiz-exercises/';
export const TEXT_EXERCISE_BASE = BASE_API + 'text-exercises/';
export const MODELING_EXERCISE_BASE = BASE_API + 'modeling-exercises';

/**
 * A class which encapsulates all cypress requests related to course management.
 */
export class CourseManagementRequests {
    /**
     * Deletes the course with the specified id.
     * @param id the course id
     * @returns <Chainable> request response
     */
    deleteCourse(id: number) {
        // Sometimes the backend fails with a ConstraintViolationError if we delete the course immediately after a login
        cy.wait(100);
        return cy.request({ method: DELETE, url: COURSE_BASE + id });
    }

    /**
     * Creates a course with the specified title and short name.
     * @param courseName the title of the course (will generate default name if not provided)
     * @param courseShortName the short name (will generate default name if not provided)
     * @returns <Chainable> request response
     */
    createCourse(courseName = 'Cypress course' + generateUUID(), courseShortName = 'cypress' + generateUUID()) {
        const course = courseTemplate;
        course.title = courseName;
        course.shortName = courseShortName;
        return cy.request({
            url: BASE_API + 'courses',
            method: POST,
            body: course,
        });
    }

    /**
     * Deletes the programming exercise with the specified id.
     * @param id the exercise id
     * @returns <Chainable> request response
     */
    deleteProgrammingExercise(id: number) {
        return cy.request({ method: DELETE, url: PROGRAMMING_EXERCISE_BASE + id + '?deleteStudentReposBuildPlans=true&deleteBaseReposBuildPlans=true' });
    }

    /**
     * Creates a course with the specified title and short name.
     * @param body an object containing either the course or exercise group the exercise will be added to
     * @param title the title of the programming exercise
     * @param programmingShortName the short name of the programming exercise
     * @param packageName the package name of the programming exercise
     * @param body an object containing either the course or exercise group the exercise will be added to
     * @param scaMaxPenalty the max percentage (0-100) static code analysis can reduce from the points (if sca should be disabled pass null)
     * @param releaseDate when the programming exercise should be available (default is now)
     * @param dueDate when the programming exercise should be due (default is now + 1 day)
     * @returns <Chainable> request response
     */
    createProgrammingExercise(
        body: { course: any } | { exerciseGroup: any },
        scaMaxPenalty?: number,
        releaseDate = day(),
        dueDate = day().add(1, 'day'),
        title = 'Cypress programming exercise ' + generateUUID(),
        programmingShortName = 'cypress' + generateUUID(),
        packageName = 'de.test',
    ) {
        const isExamExercise = body.hasOwnProperty('exerciseGroup');
        const programmingTemplate: any = this.getCourseOrExamExercise(programmingExerciseTemplate, body);
        programmingTemplate.title = title;
        programmingTemplate.shortName = programmingShortName;
        programmingTemplate.packageName = packageName;
        if (!isExamExercise) {
            programmingTemplate.releaseDate = dayjsToString(releaseDate);
            programmingTemplate.dueDate = dayjsToString(dueDate);
        } else {
            programmingTemplate.allowComplaintsForAutomaticAssessments = true;
        }

        if (scaMaxPenalty) {
            programmingTemplate.staticCodeAnalysisEnabled = true;
            programmingTemplate.maxStaticCodeAnalysisPenalty = scaMaxPenalty;
        }
        const runsOnBamboo: boolean = Cypress.env('isBamboo');
        if (runsOnBamboo) {
            cy.waitForGroupSynchronization();
        }

        return cy.request({
            url: PROGRAMMING_EXERCISE_BASE + 'setup',
            method: POST,
            body: programmingTemplate,
        });
    }

    /**
     * Adds the specified student to the course.
     * @param courseId the course id
     * @param studentName the student name
     * @returns <Chainable> request response
     */
    addStudentToCourse(courseId: number, studentName: string) {
        return this.addUserToCourse(courseId, studentName, 'students');
    }

    /**
     * Adds the specified user to the tutor group in the course
     */
    addTutorToCourse(course: any, user: CypressCredentials) {
        return this.addUserToCourse(course.id, user.username, 'tutors');
    }

    /**
     * Adds the specified user to the instructor group in the course
     */
    addInstructorToCourse(courseId: number, user: CypressCredentials) {
        return this.addUserToCourse(courseId, user.username, 'instructors');
    }

    private addUserToCourse(courseId: number, username: string, roleIdentifier: string) {
        return cy.request({ method: POST, url: `${COURSE_BASE}${courseId}/${roleIdentifier}/${username}` });
    }

    /**
     * Creates an exam with the provided settings.
     * @param exam the exam object created by a {@link CypressExamBuilder}
     * @returns <Chainable> request response
     */
    createExam(exam: any) {
        return cy.request({ url: COURSE_BASE + exam.course.id + '/exams', method: POST, body: exam });
    }

    /**
     * Deletes the exam with the given parameters
     * @returns <Chainable> request response
     * */
    deleteExam(exam: any) {
        return cy.request({ method: DELETE, url: COURSE_BASE + exam.course.id + '/exams/' + exam.id });
    }

    /**
     * register the student for the exam
     * @returns <Chainable> request response
     */
    registerStudentForExam(exam: any, student: CypressCredentials) {
        return cy.request({ method: POST, url: COURSE_BASE + exam.course.id + '/exams/' + exam.id + '/students/' + student.username });
    }

    /**
     * add exercise group to exam
     * @returns <Chainable> request response
     * */
    addExerciseGroupForExam(exam: any, title = 'group' + generateUUID(), mandatory = true) {
        exerciseGroup.exam = exam;
        exerciseGroup.title = title;
        exerciseGroup.isMandatory = mandatory;
        return cy.request({ method: POST, url: COURSE_BASE + exam.course.id + '/exams/' + exam.id + '/exerciseGroups', body: exerciseGroup });
    }

    /**
     * add text exercise to an exercise group in exam or to a course
     * @returns <Chainable> request response
     */
    createTextExercise(
        body: { course: any } | { exerciseGroup: any },
        title = 'Text exercise ' + generateUUID(),
        releaseDate = day().subtract(1, 'days'),
        dueDate = day().add(1, 'days'),
        assessmentDueDate = day().add(2, 'days'),
    ) {
        const template: any = { ...textExerciseTemplate, title };
        const textExercise: any = Object.assign({}, template, body);
        if (body.hasOwnProperty('course')) {
            textExercise.releaseDate = dayjsToString(releaseDate);
            textExercise.dueDate = dayjsToString(dueDate);
            textExercise.assessmentDueDate = dayjsToString(assessmentDueDate);
        }
        return cy.request({ method: POST, url: TEXT_EXERCISE_BASE, body: textExercise });
    }

    /**
     * generate all missing individual exams
     * @returns <Chainable> request response
     */
    generateMissingIndividualExams(exam: any) {
        return cy.request({ method: POST, url: COURSE_BASE + exam.course.id + '/exams/' + exam.id + '/generate-missing-student-exams' });
    }

    /**
     * Prepares individual exercises for exam start
     * @returns <Chainable> request response
     */
    prepareExerciseStartForExam(exam: any) {
        return cy.request({ method: POST, url: COURSE_BASE + exam.course.id + '/exams/' + exam.id + '/student-exams/start-exercises' });
    }

    createModelingExercise(
        body: { course: any } | { exerciseGroup: any },
        title = 'Cypress modeling exercise ' + generateUUID(),
        releaseDate = day(),
        dueDate = day().add(1, 'days'),
        assessmentDueDate = day().add(2, 'days'),
    ) {
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

    deleteModelingExercise(exerciseID: number) {
        return cy.request({
            url: `${MODELING_EXERCISE_BASE}/${exerciseID}`,
            method: DELETE,
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
        body: { course: any } | { exerciseGroup: any },
        quizQuestions: [any],
        title = 'Cypress quiz exercise' + generateUUID(),
        releaseDate = day(),
        duration = 600,
    ) {
        const quizExercise: any = {
            ...quizTemplate,
            title,
            releaseDate: dayjsToString(releaseDate),
            quizQuestions,
            duration,
        };
        const newQuizExercise = this.getCourseOrExamExercise(quizExercise, body);
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

    startExerciseParticipation(courseId: number, exerciseId: number) {
        return cy.request({
            url: `${COURSE_BASE}${courseId}/exercises/${exerciseId}/participations`,
            method: POST,
        });
    }

    makeTextExerciseSubmission(exerciseId: number, text: string) {
        return cy.request({
            url: `${EXERCISE_BASE}${exerciseId}/text-submissions`,
            method: PUT,
            body: { submissionExerciseType: 'text', text, id: null },
        });
    }

    updateTextExerciseDueDate(exercise: any, due = day()) {
        exercise.dueDate = dayjsToString(due);
        return this.updateTextExercise(exercise);
    }

    updateTextExerciseAssessmentDueDate(exercise: any, due = day()) {
        exercise.assessmentDueDate = dayjsToString(due);
        return this.updateTextExercise(exercise);
    }

    private updateTextExercise(exercise: any) {
        return cy.request({
            url: TEXT_EXERCISE_BASE,
            method: PUT,
            body: exercise,
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
                quizQuestion: quizExercise.quizQuestions[0],
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
                spot: quizExercise.quizQuestions[0].spots[index],
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

    startExerciseParticipation(courseId: number, exerciseId: number) {
        return cy.request({
            url: `${COURSE_BASE}${courseId}/exercises/${exerciseId}/participations`,
            method: POST,
        });
    }

    /**
     * Because the only difference between course exercises and exam exercises is the "course" or "exerciseGroup" field
     * This function takes an exercise template and adds one of the fields to it
     * @param exercise the exercise template
     * @param body the exercise group or course the exercise will be added to
     */
    private getCourseOrExamExercise(exercise: object, body: { course: any } | { exerciseGroup: any }) {
        return Object.assign({}, exercise, body);
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

    /**
     * @returns the exam object
     */
    build() {
        return this.template;
    }
}
