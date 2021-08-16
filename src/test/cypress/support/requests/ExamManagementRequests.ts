import { BASE_API, POST } from '../constants';
import { CypressCredentials } from '../users';
import textExercise from '../../fixtures/requests/exam_textExercise_template.json';
import exerciseGroup from '../../fixtures/requests/exerciseGroup_template.json';
import { COURSE_BASE, PROGRAMMING_EXERCISE_BASE } from './CourseManagementRequests';
import programmingExerciseExamTemplate from '../../fixtures/requests/programming_exercise_exam_template.json';

/**
 * A class which encapsulates all cypress requests related to managing an existing exam.
 */
export class ExamManagementRequests {
    /**
     * register the student for the exam
     * @returns <Chainable> request response
     */
    registerStudent(course: any, exam: any, student: CypressCredentials) {
        return cy.request({ method: POST, url: COURSE_BASE + course.id + '/exams/' + exam.id + '/students/' + student.username });
    }

    /**
     * add exercise group to exam
     * @returns <Chainable> request response
     * */
    addExerciseGroup(course: any, exam: any, title: string, mandatory: boolean) {
        exerciseGroup.exam = exam;
        exerciseGroup.title = title;
        exerciseGroup.isMandatory = mandatory;
        return cy.request({ method: POST, url: COURSE_BASE + course.id + '/exams/' + exam.id + '/exerciseGroups', body: exerciseGroup });
    }

    /**
     * add text exercise to exercise group in exam
     * @returns <Chainable> request response
     * */
    addTextExercise(group: any, title: string) {
        textExercise.exerciseGroup = group;
        textExercise.title = title;
        return cy.request({ method: POST, url: BASE_API + 'text-exercises', body: textExercise });
    }

    /**
     * Creates a programming exercise with the specified title and short name and adds it to the specified exercise group.
     * @param group the response object from a previous call to addExerciseGroup
     * @param title the title of the programming exercise
     * @param shortName the short name of the programming exercise
     * @param packageName the package name of the programming exercise
     * @returns <Chainable> request response
     */
    addProgrammingExercise(group: any, title: string, shortName: string, packageName: string) {
        const template = programmingExerciseExamTemplate;
        template.title = title;
        template.shortName = shortName;
        template.packageName = packageName;
        template.exerciseGroup = group;

        return cy.request({
            url: PROGRAMMING_EXERCISE_BASE + 'setup',
            method: 'POST',
            body: template,
        });
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
    prepareExerciseStart(course: any, exam: any) {
        return cy.request({ method: POST, url: COURSE_BASE + course.id + '/exams/' + exam.id + '/student-exams/start-exercises' });
    }
}
