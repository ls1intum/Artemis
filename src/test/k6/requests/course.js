import { COURSES } from "./endpoints.js";
import { nextAlphanumeric } from "./random.js";
import { COURSE } from "./endpoints.js";

export function newCourseShortName(artemis, courseId) {
    let course = JSON.parse(artemis.get(COURSE(courseId), null)[0].body);
    course.shortName = "TEST" + nextAlphanumeric(5);
    artemis.put(COURSES, course);
}