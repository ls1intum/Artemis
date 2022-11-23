import { COURSE, COURSES, COURSE_STUDENTS, COURSE_INSTRUCTORS, COURSE_TUTORS, ADMIN_COURSES, ADMIN_COURSE } from './endpoints.js';
import { nextAlphanumeric } from '../util/utils.js';
import { fail } from 'k6';
import http from 'k6/http';
import { FormData } from 'https://jslib.k6.io/formdata/0.0.2/index.js';

export function getCourse(artemis, courseId) {
    const res = JSON.parse(artemis.get(COURSE(courseId), null));
    if (res[0].status !== 200) {
        console.log('ERROR when getting existing course. Response headers:');
        for (let [key, value] of Object.entries(res[0].headers)) {
            console.log(`${key}: ${value}`);
        }
        fail('FAILTEST: Could not get course (status: ' + res[0].status + ')! response: ' + res[0].body);
    }
    console.log('SUCCESS: Get existing course');

    return JSON.parse(res[0].body);
}

export function newCourseShortName(artemis, courseId) {
    const course = JSON.parse(artemis.get(COURSE(courseId), null)[0].body);
    course.shortName = 'TEST' + nextAlphanumeric(5);
    artemis.put(COURSES, course);
}

export function newCourse(artemis) {
    const currentDate = new Date();
    const startDate = new Date(currentDate.getTime() - 2 * 60 * 60 * 1000); // - 2h
    const endDate = new Date(currentDate.getTime() + 2 * 60 * 60 * 1000); // + 2h
    const course = {
        title: 'K6 Test Course',
        description: 'K6 performance tests generated course',
        shortName: 'testk6' + nextAlphanumeric(5),
        registrationEnabled: false,
        maxComplaints: 3,
        maxComplaintTimeDays: 7,
        accuracyOfScores: 1,
        startDate: startDate,
        endDate: endDate,
    };

    const formData = new FormData();
    formData.append('course', http.file(JSON.stringify(course), 'course', 'application/json'));

    const res = artemis.post(ADMIN_COURSES, undefined, undefined, formData);
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

export function addUserToTutorsInCourse(artemis, username, courseId) {
    const res = artemis.post(COURSE_TUTORS(courseId, username));
    console.log('Add user ' + username + ' to tutors in course ' + courseId + ' status: ' + res[0].status);
}

export function removeUserFromTutorsInCourse(artemis, username, courseId) {
    const res = artemis.delete(COURSE_TUTORS(courseId, username));
    console.log('Remove user ' + username + ' from tutors in course ' + courseId + ' status: ' + res[0].status);
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
    const res = artemis.delete(ADMIN_COURSE(courseId));

    if (res[0].status !== 200) {
        fail('FAILTEST: Unable to delete course ' + courseId);
    }
    console.log('SUCCESS: Deleted course ' + courseId);
}
