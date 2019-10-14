import { COURSES } from "./endpoints.js";
import { nextAlphanumeric } from "./random.js";
import { COURSE } from "./endpoints.js";

export function newCourseShortName(artemis, courseId) {
    let course = JSON.parse(artemis.get(COURSE(courseId), null)[0].body);
    course.shortName = "TEST" + nextAlphanumeric(5);
    artemis.put(COURSES, course);
}

export function newCourse(artemis) {
    const course = {
        title: "K6 Test Course",
        description: "K6 performance tests generated course",
        shortName: nextAlphanumeric(5),
        studentGroupName: "artemis-test",
        teachingAssistantGroupName: "artemis-test",
        instructorGroupName: "artemis-test",
        registrationEnabled: true
    };

    return JSON.parse(artemis.post(COURSES, course)[0].body).id;
}

export function deleteCourse(artemis, courseId) {
    artemis.delete(COURSE(courseId));
}
