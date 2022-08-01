import {
    EVALUATE_QUIZ_EXAM,
    EXAM_CONDUCTION,
    EXAM_START,
    EXAM_STUDENTS,
    EXAMS,
    EXERCISE_GROUPS,
    GENERATE_STUDENT_EXAMS,
    START_EXERCISES,
    STUDENT_EXAM_WORKINGTIME,
    STUDENT_EXAMS,
    SUBMIT_EXAM,
} from './endpoints.js';
import { nextAlphanumeric } from '../util/utils.js';
import { fail } from 'k6';

export function newExam(artemis, course) {
    const currentDate = new Date();
    const visibleDate = new Date(currentDate.getTime() + 30000); // Visible in 30 secs
    const startDate = new Date(currentDate.getTime() + 60000); // Starting in 60 secs
    const endDate = new Date(currentDate.getTime() + 600000); // Ending in 600 secs

    const exam = {
        course: course,
        visibleDate: visibleDate,
        startDate: startDate,
        endDate: endDate,
        maxPoints: 54,
        numberOfExercisesInExam: 4,
        randomizeExerciseOrder: false,
        started: false,
        title: 'Exam K6 ' + nextAlphanumeric(5),
        visible: false,
        gracePeriod: 180,
    };

    const res = artemis.post(EXAMS(course.id), exam);
    if (res[0].status !== 201) {
        console.log('ERROR when creating a new exam. Response headers:');
        for (let [key, value] of Object.entries(res[0].headers)) {
            console.log(`${key}: ${value}`);
        }
        fail('FAILTEST: Could not create exam (status: ' + res[0].status + ')! response: ' + res[0].body);
    }
    console.log('SUCCESS: Generated new exam');

    return JSON.parse(res[0].body);
}

export function newExerciseGroup(artemis, exam, mandatory = true) {
    const exerciseGroup = {
        exam: exam,
        isMandatory: mandatory,
        title: 'Group K6 ' + nextAlphanumeric(5),
    };

    const res = artemis.post(EXERCISE_GROUPS(exam.course.id, exam.id), exerciseGroup);
    if (res[0].status !== 201) {
        console.log('ERROR when creating a new exercise group. Response headers:');
        for (let [key, value] of Object.entries(res[0].headers)) {
            console.log(`${key}: ${value}`);
        }
        fail('FAILTEST: Could not create exercise group (status: ' + res[0].status + ')! response: ' + res[0].body);
    }
    console.log('SUCCESS: Generated new exercise group');

    return JSON.parse(res[0].body);
}

export function addUserToStudentsInExam(artemis, username, exam) {
    const res = artemis.post(EXAM_STUDENTS(exam.course.id, exam.id, username));
    console.log('Add user ' + username + ' to students in exam ' + exam.id + ' status: ' + res[0].status);
}

export function generateExams(artemis, exam) {
    const res = artemis.post(GENERATE_STUDENT_EXAMS(exam.course.id, exam.id));
    console.log('Generated student exams in exam ' + exam.id + ' status: ' + res[0].status);
}

export function startExercises(artemis, exam) {
    const res = artemis.post(START_EXERCISES(exam.course.id, exam.id));
    console.log('Start exercises for exam ' + exam.id + ' status: ' + res[0].status);
}

export function startStudentExamForUser(artemis, courseId, examId) {
    const res = artemis.get(EXAM_START(courseId, examId));
    console.log('Started student exam for exam ' + examId + ' status: ' + res[0].status);

    return JSON.parse(res[0].body);
}

export function getExamForUser(artemis, courseId, examId, studentExamId) {
    const res = artemis.get(EXAM_CONDUCTION(courseId, examId, studentExamId));
    console.log('Retrieved student exam for exam ' + examId + ' status: ' + res[0].status);

    return JSON.parse(res[0].body);
}

export function getStudentExams(artemis, exam) {
    const res = artemis.get(STUDENT_EXAMS(exam.course.id, exam.id));
    console.log('Retrieved student exams for exam ' + exam.id + ' status: ' + res[0].status);

    return JSON.parse(res[0].body);
}

export function updateWorkingTime(artemis, exam, studentExam, workingTime) {
    const res = artemis.patch(STUDENT_EXAM_WORKINGTIME(exam.course.id, exam.id, studentExam.id), workingTime);
    console.log('Updated student Exam for exam ' + exam.id + ' status: ' + res[0].status);
}

export function evaluateQuizzes(artemis, courseId, examId) {
    const res = artemis.post(EVALUATE_QUIZ_EXAM(courseId, examId));
    console.log('Evaluated quiz exercises in exam ' + examId + ' status: ' + res[0].status);
}

export function submitExam(artemis, courseId, examId, studentExam) {
    const res = artemis.post(SUBMIT_EXAM(courseId, examId), studentExam);
    console.log('Evaluated quiz exercises in exam ' + examId + ' status: ' + res[0].status);
}
