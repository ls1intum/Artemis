import { COURSE, COURSES, COURSE_STUDENTS, COURSE_INSTRUCTORS, EXAMS, EXERCISE_GROUPS, TEXT_EXERCISE } from './endpoints.js';
import { nextAlphanumeric } from '../util/utils.js';
import { fail } from 'k6';

export function newExam(artemis, course) {
    const exam = {
        course: course,
        visibleDate: '2020-07-07T13:20:00.000Z',
        startDate: '2020-07-07T13:25:00.000Z',
        endDate: '2020-07-07T13:35:00.000Z',
        maxPoints: 10,
        numberOfExercisesInExam: 4,
        randomizeExerciseOrder: false,
        started: false,
        title: 'exam' + nextAlphanumeric(5),
        visible: false,
    };

    const res = artemis.post(EXAMS(course.id), exam);
    if (res[0].status !== 201) {
        console.log('ERROR when creating a new exam. Response headers:');
        for (let [key, value] of Object.entries(res[0].headers)) {
            console.log(`${key}: ${value}`);
        }
        fail('ERROR: Could not create exam (status: ' + res[0].status + ')! response: ' + res[0].body);
    }
    console.log('SUCCESS: Generated new exam');

    return JSON.parse(res[0].body);
}

export function newExerciseGroup(artemis, exam, mandatory = true) {
    const exerciseGroup = {
        exam: exam,
        isMandatory: mandatory,
        title: 'group' + nextAlphanumeric(5),
    };

    const res = artemis.post(EXERCISE_GROUPS(exam.course.id, exam.id), exerciseGroup);
    if (res[0].status !== 201) {
        console.log('ERROR when creating a new exercise group. Response headers:');
        for (let [key, value] of Object.entries(res[0].headers)) {
            console.log(`${key}: ${value}`);
        }
        fail('ERROR: Could not create exercise group (status: ' + res[0].status + ')! response: ' + res[0].body);
    }
    console.log('SUCCESS: Generated new exercise group');

    return JSON.parse(res[0].body);
}

export function newTextExercise(artemis, exerciseGroup) {
    const textExercise = {
        exerciseGroup: exerciseGroup,
        maxScore: 1,
        title: 'text' + nextAlphanumeric(5),
        type: 'text',
        mode: 'INDIVIDUAL',
    };

    const res = artemis.post(TEXT_EXERCISE, textExercise);
    if (res[0].status !== 201) {
        console.log('ERROR when creating a new text exercise. Response headers:');
        for (let [key, value] of Object.entries(res[0].headers)) {
            console.log(`${key}: ${value}`);
        }
        fail('ERROR: Could not create text exercise (status: ' + res[0].status + ')! response: ' + res[0].body);
    }
    console.log('SUCCESS: Generated new text exercise');

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
        fail('ERROR: Unable to delete course ' + courseId);
    }
    console.log('SUCCESS: Deleted course ' + courseId);
}
