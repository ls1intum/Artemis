import { COURSE, COURSES } from './endpoints.js';
import { nextAlphanumeric } from '../util/utils.js';
import { fail } from 'k6';

export function newCourseShortName(artemis, courseId) {
    const course = JSON.parse(artemis.get(COURSE(courseId), null)[0].body);
    course.shortName = 'TEST' + nextAlphanumeric(5);
    artemis.put(COURSES, course);
}

export function newCourse(artemis) {
    const course = {
        title: 'K6 Test Course',
        description: 'K6 performance tests generated course',
        shortName: nextAlphanumeric(5),
        studentGroupName: 'artemis-test',
        teachingAssistantGroupName: 'artemis-test',
        instructorGroupName: 'artemis-test',
        registrationEnabled: true,
        maxComplaints: 3,
        maxComplaintTimeDays: 7,
    };

    const res = artemis.post(COURSES, course);
    if (res[0].status !== 201) {
        fail('ERROR: Unable to generate new course');
    }
    console.log('SUCCESS: Generated new course');

    return JSON.parse(res[0].body).id;
}

export function deleteCourse(artemis, courseId) {
    const res = artemis.delete(COURSE(courseId));

    if (res[0].status !== 200) {
        fail('ERROR: Unable to delete course ' + courseId);
    }
    console.log('SUCCESS: Deleted course ' + courseId);
}
