import { BASE_API, DELETE } from '../constants';
import courseTemplate from '../../fixtures/course.json';
import programmingExerciseTemplate from '../../fixtures/requests/programming_exercise_template.json';

const COURSE_BASE = BASE_API + 'courses/';
const PROGRAMMING_EXERCISE_BASE = BASE_API + 'programming-exercises/';
const oneHour = 60 * 60 * 1000;

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
     * @param courseName the title of the course
     * @param courseShortName the short name
     * @returns the cypress chainable from the request
     */
    createProgrammingExercise(course: any, title: string, programmingShortName: string, packageName: string, releaseDate = new Date(), dueDate = new Date(Date.now() + oneHour)) {
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
}
