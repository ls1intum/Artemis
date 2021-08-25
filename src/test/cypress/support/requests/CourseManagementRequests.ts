import { ExerciseRequests } from './ExerciseRequests';
import { BASE_API, DELETE, POST } from '../constants';
import courseTemplate from '../../fixtures/requests/course.json';
import { CypressCredentials } from '../users';

export const COURSE_BASE = BASE_API + 'courses/';

/**
 * A class which encapsulates all cypress requests related to course management.
 */
export class CourseManagementRequests extends ExerciseRequests {
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
            method: POST,
            body: course,
        });
    }

    /**
     * Adds the specified student to the course.
     * @param courseId the course id
     * @param studentName the student name
     * @returns <Chainable> request response
     */
    addStudentToCourse(courseId: number, studentName: string) {
        return cy.request({ url: COURSE_BASE + courseId + '/students/' + studentName, method: POST });
    }

    /**
     * Adds the specified user to the tutor group in the course
     */
    addTutorToCourse(course: any, user: CypressCredentials) {
        return cy.request({ method: POST, url: COURSE_BASE + course.id + '/tutors/' + user.username });
    }

    /**
     * Creates a programming exercise with the specified settings and adds it to the provided course.
     * @param title the title of the programming exercise
     * @param programmingShortName the short name of the programming exercise
     * @param packageName the package name of the programming exercise
     * @param course the course object returned by a create course request
     * @param releaseDate when the programming exercise should be available (default is now)
     * @param dueDate when the programming exercise should be due (default is now + 1 day)
     * @returns <Chainable> request response
     */
    createProgrammingExercise(
        title: string,
        programmingShortName: string,
        packageName: string,
        course: any,
        releaseDate = new Date(),
        dueDate = new Date(Date.now() + this.oneDay),
    ) {
        return super.createProgrammingExercise(title, programmingShortName, packageName, { course }, releaseDate, dueDate);
    }

    /**
     * Creates a text exercise with the specified settings and adds it to the specified course.
     * @param title the title of the text exercise
     * @param course the course object
     * @returns <Chainable> request response
     */
    createTextExercise(title: string, course: any) {
        return super.createTextExercise(title, { course });
    }

    /**
     * Creates a modeling exercise with the specified settings and adds it to the specified course.
     * @param modelingExercise the modeling exercise object
     * @param course the course object
     * @returns <Chainable> request
     */
    createModelingExercise(modelingExercise: any, course: any) {
        return super.createModelingExercise(modelingExercise, { course });
    }
}
