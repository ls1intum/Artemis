import { BASE_API, DELETE, POST } from '../constants';
import courseTemplate from '../../fixtures/requests/course.json';
import programmingExerciseTemplate from '../../fixtures/requests/programming_exercise_template.json';
import { dayjsToString, generateUUID } from '../utils';
import examTemplate from '../../fixtures/requests/exam_template.json';
import day from 'dayjs';

const COURSE_BASE = BASE_API + 'courses/';
const PROGRAMMING_EXERCISE_BASE = BASE_API + 'programming-exercises/';
const oneDay = 24 * 60 * 60 * 1000;

/**
 * A class which encapsulates all cypress requests related to course management.
 */
export class CourseManagementRequests {
    /**
     * Deletes the course with the specified id.
     * @param id the course id
     * @returns the cypress chainable of the request
     */
    deleteCourse(id: number) {
        return cy.request({ method: DELETE, url: COURSE_BASE + id });
    }

    /**
     * Creates a course with the specified title and short name.
     * @param courseName the title of the course
     * @param courseShortName the short name
     * @returns the cypress chainable from the request
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
     * @returns the cypress chainable of the request
     */
    deleteProgrammingExercise(id: number) {
        return cy.request({ method: DELETE, url: PROGRAMMING_EXERCISE_BASE + id + '?deleteStudentReposBuildPlans=true&deleteBaseReposBuildPlans=true' });
    }

    /**
     * Creates a course with the specified title and short name.
     * @param course the response object from a previous call to createCourse
     * @param title the title of the programming exercise
     * @param programmingShortName the short name of the programming exercise
     * @param packageName the package name of the programming exercise
     * @param releaseDate when the programming exercise should be available (default is now)
     * @param dueDate when the programming exercise should be due (default is now + 1 day)
     * @returns the cypress chainable from the request
     */
    createProgrammingExercise(course: any, title: string, programmingShortName: string, packageName: string, releaseDate = new Date(), dueDate = new Date(Date.now() + oneDay)) {
        const programmingTemplate = programmingExerciseTemplate;
        programmingTemplate.title = title;
        programmingTemplate.shortName = programmingShortName;
        programmingTemplate.packageName = packageName;
        programmingTemplate.releaseDate = releaseDate.toISOString();
        programmingTemplate.dueDate = dueDate.toISOString();
        programmingTemplate.course = course;

        return cy.request({
            url: PROGRAMMING_EXERCISE_BASE + 'setup',
            method: 'POST',
            body: programmingTemplate,
        });
    }

    /**
     * Adds the specified student to the course.
     * @param courseId the course id
     * @param studentName the student name
     * @returns the cypress chainable of the request
     */
    addStudentToCourse(courseId: number, studentName: string) {
        return cy.request({ url: COURSE_BASE + courseId + '/students/' + studentName, method: POST });
    }

    /**
     * Creates an exam with the provided settings.
     * @param exam the exam object created by a {@link CypressExamBuilder}
     * @returns the request response
     */
    createExam(exam: any) {
        return cy.request({ url: COURSE_BASE + exam.course.id + '/exams', method: POST, body: exam });
    }
}

/**
 * Helper class to construct exam objects for the {@link CourseManagementRequests.createExam} method.
 */
export class CypressExamBuilder {
    readonly template = examTemplate;

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

    /**
     * @returns the exam object
     */
    build() {
        return this.template;
    }
}
