import { POST } from '../constants';
import { CypressCredentials } from '../users';
import exerciseGroup from '../../fixtures/requests/exerciseGroup_template.json';
import { COURSE_BASE } from './CourseManagementRequests';

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
