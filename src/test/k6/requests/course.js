import { COURSE, COURSES, COURSE_STUDENTS, COURSE_INSTRUCTORS } from './endpoints.js';
import { nextAlphanumeric } from '../util/utils.js';
import { fail } from 'k6';

export function newCourseShortName(artemis, courseId) {
    const course = JSON.parse(artemis.get(COURSE(courseId), null)[0].body);
    course.shortName = 'TEST' + nextAlphanumeric(5);
    artemis.put(COURSES, course);
}

export function newCourse(artemis) {
    const course = {
        title: 'Test Course',
        description: 'K6 performance tests generated course',
        shortName: 'test' + nextAlphanumeric(5),
        registrationEnabled: false,
        maxComplaints: 3,
        maxComplaintTimeDays: 7,
    };

    const res = artemis.post(COURSES, course);
    if (res[0].status !== 201) {
        console.log('ERROR when creating a new course. Response headers:');
        for (let [key, value] of Object.entries(res[0].headers)) {
            console.log(`${key}: ${value}`);
        }
        fail('FAILTEST: Could not create course (status: ' + res[0].status + ')! response: ' + res[0].body);
    }
    console.log('SUCCESS: Generated new course');

    return JSON.parse(res[0].body);
}

export function addUserToStudentsInCourse(artemis, username, courseId) {
    const res = artemis.post(COURSE_STUDENTS(courseId, username));
    console.log('Add user ' + username + ' to students in course ' + courseId + ' status: ' + res[0].status);
}

export function removeUserFromStudentsInCourse(artemis, username, courseId) {
    const res = artemis.delete(COURSE_STUDENTS(courseId, username));
    console.log('Remove user ' + username + ' from students in course ' + courseId + ' status: ' + res[0].status);
}

export function addUserToInstructorsInCourse(artemis, username, courseId) {
    const res = artemis.post(COURSE_INSTRUCTORS(courseId, username));
    console.log('Add user ' + username + ' to instructors in course ' + courseId + ' status: ' + res[0].status);
}

export function removeUserFromInstructorsInCourse(artemis, username, courseId) {
    const res = artemis.delete(COURSE_INSTRUCTORS(courseId, username));
    console.log('Remove user ' + username + ' from instructors in course ' + courseId + ' status: ' + res[0].status);
}

export function deleteCourse(artemis, courseId) {
    const res = artemis.delete(COURSE(courseId));

    if (res[0].status !== 200) {
        fail('FAILTEST: Unable to delete course ' + courseId);
    }
    console.log('SUCCESS: Deleted course ' + courseId);
}
