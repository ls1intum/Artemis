import { ExerciseRequests } from './ExerciseRequests';
import { DELETE, POST } from '../constants';
import { CypressCredentials } from '../users';
import exerciseGroup from '../../fixtures/requests/exerciseGroup_template.json';
import { COURSE_BASE } from './CourseManagementRequests';
import day from 'dayjs';
import examTemplate from '../../fixtures/requests/exam_template.json';
import { dayjsToString, generateUUID } from '../utils';

/**
 * Requests related to exams.
 */
export class ExamManagementRequests extends ExerciseRequests {
    /**
     * register the student for the exam
     * @returns <Chainable> request response
     */
    registerStudent(exam: any, student: CypressCredentials) {
        return cy.request({ method: POST, url: COURSE_BASE + exam.course.id + '/exams/' + exam.id + '/students/' + student.username });
    }

    /**
     * Creates an exercise group and adds it to the specified exam.
     * @param exam the exam
     * @param title the title of the exercise group
     * @param isMandatory whether the exercise group is mandatory
     * @returns <Chainable> request
     * */
    createExerciseGroup(exam: any, title = 'group' + generateUUID(), isMandatory = true) {
        const group = { ...exerciseGroup, exam, title, isMandatory };
        return cy.request({ method: POST, url: COURSE_BASE + exam.course.id + '/exams/' + exam.id + '/exerciseGroups', body: group });
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
    prepareExerciseStart(exam: any) {
        return cy.request({ method: POST, url: COURSE_BASE + exam.course.id + '/exams/' + exam.id + '/student-exams/start-exercises' });
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
     * Creates a programming exercise with the specified settings and adds it to the provided course.
     * @param title the title of the programming exercise
     * @param programmingShortName the short name of the programming exercise
     * @param packageName the package name of the programming exercise
     * @param course the exercise group object returned by a {@link ExamManagementRequests.createExerciseGroup} request
     * @param releaseDate when the programming exercise should be available (default is now)
     * @param dueDate when the programming exercise should be due (default is now + 1 day)
     * @returns <Chainable> request response
     */
    createProgrammingExercise(
        exerciseGroup: any,
        title = 'Cypress programming exercise ' + generateUUID(),
        programmingShortName = 'cypress' + generateUUID(),
        packageName = 'de.test',
        releaseDate = day(),
        dueDate = day().add(1, 'days'),
    ) {
        return super.createProgrammingExercise({ exerciseGroup }, title, programmingShortName, packageName, releaseDate, dueDate);
    }

    /**
     * Creates a text exercise with the specified settings and adds it to the specified exercise group.
     * @param exerciseGroup the exercise group object
     * @param title the title of the text exercise
     * @returns <Chainable> request response
     */
    createTextExercise(exerciseGroup: any, title = 'Text exercise ' + generateUUID()) {
        return super.createTextExercise({ exerciseGroup }, title);
    }

    /**
     * Creates a modeling exercise with the specified settings and adds it to the specified course.
     * @param modelingExercise the modeling exercise object
     * @param exerciseGroup the exercise group object
     * @returns <Chainable> request
     */
    createModelingExercise(modelingExercise: any, exerciseGroup: any) {
        return super.createModelingExercise(modelingExercise, { exerciseGroup });
    }
}

/**
 * Helper class to construct exam objects for the {@link ExamManagementRequests.createExam} method.
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
