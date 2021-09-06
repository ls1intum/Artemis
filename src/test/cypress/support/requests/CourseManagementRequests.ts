import { BASE_API, DELETE, POST, PUT } from '../constants';
import courseTemplate from '../../fixtures/requests/course.json';
import programmingExerciseTemplate from '../../fixtures/requests/programming_exercise_template.json';
import { dayjsToString, generateUUID } from '../utils';
import examTemplate from '../../fixtures/requests/exam_template.json';
import day from 'dayjs';
import { CypressCredentials } from '../users';
import textExerciseTemplate from '../../fixtures/requests/textExercise_template.json';
import exerciseGroup from '../../fixtures/requests/exerciseGroup_template.json';
import assessment_submission from '../../fixtures/programming_exercise_submissions/assessment/submission.json';

export const COURSE_BASE = BASE_API + 'courses/';
export const PROGRAMMING_EXERCISE_BASE = BASE_API + 'programming-exercises/';
export const TEXT_EXERCISE_BASE = BASE_API + 'text-exercises/';
export const EXERCISE_BASE = BASE_API + 'exercises/';
const oneDay = 24 * 60 * 60 * 1000;

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
        return cy.request({ method: DELETE, url: COURSE_BASE + id });
    }

    /**
     * Creates a course with the specified title and short name.
     * @param courseName the title of the course
     * @param courseShortName the short name
     * @returns <Chainable> request response
     */
    createCourse(courseName: string, courseShortName: string) {
        const course = courseTemplate;
        course.title = courseName;
        course.shortName = courseShortName;
        return cy.request({
            url: BASE_API + 'courses',
            method: 'POST',
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
     * @param title the title of the programming exercise
     * @param programmingShortName the short name of the programming exercise
     * @param packageName the package name of the programming exercise
     * @param body an object containing either the course or exercise group the exercise will be added to
     * @param releaseDate when the programming exercise should be available (default is now)
     * @param dueDate when the programming exercise should be due (default is now + 1 day)
     * @param assessmentType the assessment type of the exercise (default is AUTOMATIC)
     * @returns <Chainable> request
     */
    createProgrammingExercise(
        title: string,
        programmingShortName: string,
        packageName: string,
        body: { course: any } | { exerciseGroup: any },
        releaseDate = day(),
        dueDate = day().add(1, 'day'),
        assessmentDueDate = day().add(2, 'days'),
        assessmentType = CypressAssessmentType.AUTOMATIC,
    ) {
        const isExamExercise = body.hasOwnProperty('exerciseGroup');
        const template = { ...programmingExerciseTemplate, title, shortName: programmingShortName, packageName, assessmentType: assessmentType.toString(), assessmentDueDate };
        const exercise: any = Object.assign({}, template, body);
        if (!isExamExercise) {
            exercise.releaseDate = dayjsToString(releaseDate);
            exercise.dueDate = dayjsToString(dueDate);
        } else {
            exercise.allowComplaintsForAutomaticAssessments = true;
        }

        const runsOnBamboo: boolean = Cypress.env('isBamboo');
        if (runsOnBamboo) {
            cy.waitForGroupSynchronization();
        }

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

    updateProgrammingExerciseDueDate(exercise: any, due = day()) {
        exercise.dueDate = dayjsToString(due);
        return this.updateExercise(exercise, CypressExerciseType.PROGRAMMING);
    }

    updateProgrammingExerciseAssessmentDueDate(exercise: any, due = day()) {
        exercise.assessmentDueDate = dayjsToString(due);
        return this.updateExercise(exercise, CypressExerciseType.PROGRAMMING);
    }

    private updateExercise(exercise: any, type: CypressExerciseType) {
        let url: string;
        switch (type) {
            case CypressExerciseType.PROGRAMMING:
                url = PROGRAMMING_EXERCISE_BASE;
                break;
            case CypressExerciseType.TEXT:
                url = TEXT_EXERCISE_BASE;
                break;
            case CypressExerciseType.MODELING:
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
     */
    deleteExam(course: any, exam: any) {
        return cy.request({ method: DELETE, url: COURSE_BASE + course.id + '/exams/' + exam.id });
    }

    /**
     * register the student for the exam
     * @returns <Chainable> request response
     */
    registerStudentForExam(course: any, exam: any, student: CypressCredentials) {
        return cy.request({ method: POST, url: COURSE_BASE + course.id + '/exams/' + exam.id + '/students/' + student.username });
    }

    /**
     * add exercise group to exam
     * @returns <Chainable> request response
     */
    addExerciseGroupForExam(course: any, exam: any, title: string, mandatory: boolean) {
        exerciseGroup.exam = exam;
        exerciseGroup.title = title;
        exerciseGroup.isMandatory = mandatory;
        return cy.request({ method: POST, url: COURSE_BASE + course.id + '/exams/' + exam.id + '/exerciseGroups', body: exerciseGroup });
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
        const textExercise: any = this.getCourseOrExamExercise(textExerciseTemplate, body);
        textExercise.title = title;
        textExercise.releaseDate = dayjsToString(releaseDate);
        textExercise.dueDate = dayjsToString(dueDate);
        textExercise.assessmentDueDate = dayjsToString(assessmentDueDate);
        return cy.request({ method: POST, url: TEXT_EXERCISE_BASE, body: textExercise });
    }

    /**
     * generate all missing individual exams
     * @returns <Chainable> request response
     */
    generateMissingIndividualExams(course: any, exam: any) {
        return cy.request({ method: POST, url: COURSE_BASE + course.id + '/exams/' + exam.id + '/generate-missing-student-exams' });
    }

    /**
     * Prepares individual exercises for exam start
     * @returns <Chainable> request response
     */
    prepareExerciseStartForExam(course: any, exam: any) {
        return cy.request({ method: POST, url: COURSE_BASE + course.id + '/exams/' + exam.id + '/student-exams/start-exercises' });
    }

    createModelingExercise(modelingExercise: any, body: { course: any } | { exerciseGroup: any }) {
        const newModelingExercise = this.getCourseOrExamExercise(modelingExercise, body);
        return cy.request({
            url: '/api/modeling-exercises',
            method: 'POST',
            body: newModelingExercise,
        });
    }

    deleteModelingExercise(exerciseID: number) {
        return cy.request({
            url: `/api/modeling-exercises/${exerciseID}`,
            method: 'DELETE',
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
        return this.updateExercise(exercise, CypressExerciseType.TEXT);
    }

    updateTextExerciseAssessmentDueDate(exercise: any, due = day()) {
        exercise.assessmentDueDate = dayjsToString(due);
        return this.updateExercise(exercise, CypressExerciseType.TEXT);
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
}
