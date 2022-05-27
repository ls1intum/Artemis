import { login } from './requests/requests.js';
import { group, sleep } from 'k6';
import { newCourse, deleteCourse, addUserToInstructorsInCourse } from './requests/course.js';
import { createUsersIfNeeded } from './requests/user.js';
import { createQuizExercise, submitRandomAnswerRESTExam } from './requests/quiz.js';
import {
    newExam,
    newExerciseGroup,
    addUserToStudentsInExam,
    generateExams,
    startExercises,
    getExamForUser,
    getStudentExams,
    updateWorkingTime,
    evaluateQuizzes,
    submitExam,
} from './requests/exam.js';
import { submitRandomTextAnswerExam, newTextExercise } from './requests/text.js';
import { newModelingExercise, submitRandomModelingAnswerExam } from './requests/modeling.js';
import { createProgrammingExercise, ParticipationSimulation, simulateSubmission, TestResult } from './requests/programmingExercise.js';
import { someSuccessfulErrorContentJava, allSuccessfulContentJava, buildErrorContentJava } from './resource/constants_java.js';

// Version: 1.1
// Creator: Firefox
// Browser: Firefox

export let options = {
    maxRedirects: 0,
    iterations: __ENV.ITERATIONS,
    vus: __ENV.ITERATIONS,
    rps: 5,
    setupTimeout: '480s',
    teardownTimeout: '240s',
};

const adminUsername = __ENV.ADMIN_USERNAME;
const adminPassword = __ENV.ADMIN_PASSWORD;
let baseUsername = __ENV.BASE_USERNAME;
let basePassword = __ENV.BASE_PASSWORD;
let userOffset = parseInt(__ENV.USER_OFFSET);
const onlyPrepare = __ENV.ONLY_PREPARE === true || __ENV.ONLY_PREPARE === 'true';
// Use users with ID >= 100 to avoid manual testers entering the wrong password too many times interfering with tests
const userIdOffset = 99;

export function setup() {
    console.log('__ENV.CREATE_USERS: ' + __ENV.CREATE_USERS);
    console.log('__ENV.TIMEOUT_PARTICIPATION: ' + __ENV.TIMEOUT_PARTICIPATION);
    console.log('__ENV.TIMEOUT_EXERCISE: ' + __ENV.TIMEOUT_EXERCISE);
    console.log('__ENV.ITERATIONS: ' + __ENV.ITERATIONS);
    console.log('__ENV.USER_OFFSET: ' + __ENV.USER_OFFSET);
    console.log('__ENV.ONLY_PREPARE: ' + onlyPrepare);

    const iterations = parseInt(__ENV.ITERATIONS);

    if (parseInt(__ENV.COURSE_ID) === 0 || parseInt(__ENV.EXERCISE_ID) === 0) {
        console.log('Creating new course and exercise as no parameters are given');

        // Create course
        const artemisAdmin = login(adminUsername, adminPassword);

        const course = newCourse(artemisAdmin);

        createUsersIfNeeded(artemisAdmin, baseUsername, basePassword, adminUsername, adminPassword, course, userOffset);

        const instructorUsername = baseUsername.replace('USERID', '101');
        const instructorPassword = basePassword.replace('USERID', '101');

        addUserToInstructorsInCourse(artemisAdmin, instructorUsername, course.id);

        // Login to Artemis
        const artemis = login(instructorUsername, instructorPassword);

        // it might be necessary that the newly created groups or accounts are synced with the version control and continuous integration servers, so we wait for 1 minute
        const timeoutExercise = parseFloat(__ENV.TIMEOUT_EXERCISE);
        if (timeoutExercise > 0) {
            console.log('Wait ' + timeoutExercise + 's before creating the exam so that the setup can finish properly');
            sleep(timeoutExercise);
        }

        // Create new exam
        const exam = newExam(artemis, course);

        const exerciseGroup1 = newExerciseGroup(artemis, exam);
        const exerciseGroup2 = newExerciseGroup(artemis, exam);
        const exerciseGroup3 = newExerciseGroup(artemis, exam);
        const exerciseGroup4 = newExerciseGroup(artemis, exam);

        newTextExercise(artemis, exerciseGroup1);

        createQuizExercise(artemis, undefined, exerciseGroup2, false, false);

        newModelingExercise(artemis, exerciseGroup3);

        createProgrammingExercise(artemis, undefined, exerciseGroup4, 'JAVA', false);

        for (let i = 1 + userIdOffset; i <= iterations + userIdOffset; i++) {
            addUserToStudentsInExam(artemis, baseUsername.replace('USERID', i + userOffset), exam);
        }

        generateExams(artemis, exam);

        const studentExams = getStudentExams(artemis, exam);

        for (let index in studentExams) {
            if (index % 10 === 0) {
                const studentExam = studentExams[index];
                updateWorkingTime(artemis, exam, studentExam, 180);
            }
        }

        if (onlyPrepare) {
            return;
        }

        startExercises(artemis, exam);

        sleep(2);

        return { courseId: exam.course.id, examId: exam.id };
    } else {
        console.log('Using existing course and exercise');
        return { examId: parseInt(__ENV.EXERCISE_ID), courseId: parseInt(__ENV.COURSE_ID) };
    }
}

export default function (data) {
    if (onlyPrepare) {
        return;
    }
    const websocketConnectionTime = parseFloat(__ENV.TIMEOUT_PARTICIPATION); // Time in seconds the websocket is kept open, if set to 0 no websocket connection is estahblished

    // Delay so that not all users start at the same time, batches of 50 users per second
    const delay = Math.floor(__VU / 50);
    sleep(delay);

    group('Artemis Exam Stresstest', function () {
        const userId = parseInt(__VU) + userOffset + userIdOffset;
        const currentUsername = baseUsername.replace('USERID', userId);
        const currentPassword = basePassword.replace('USERID', userId);
        const artemis = login(currentUsername, currentPassword);

        sleep(30);

        const studentExam = getExamForUser(artemis, data.courseId, data.examId);

        console.log(studentExam.exam.startDate);
        const parsedStartDate = new Date(Date.parse(studentExam.exam.startDate));

        console.log(parsedStartDate);

        const currentTime = Date.now();
        const differenceInMilliSeconds = parsedStartDate - currentTime;
        const timeUntilExamStart = differenceInMilliSeconds / 1000;

        console.log(`Waiting ${timeUntilExamStart}s for exam start`);

        const workingTime = studentExam.workingTime;
        const individualEndDate = new Date(parsedStartDate.getTime() + workingTime * 1000);

        artemis.websocket(function (socket) {
            socket.setInterval(function timeout() {
                socket.close();
            }, individualEndDate.getTime() - Date.now());

            socket.setTimeout(function timeout() {
                console.log('Sleeping now');

                sleep(1 + timeUntilExamStart);

                console.log('Individual end date: ' + individualEndDate.toISOString());

                console.log('Remaining: ' + (individualEndDate.getTime() - Date.now()));

                let programmingSubmissionCounter = 0;
                endDateLoop: while (true) {
                    let submissions, submissionId;
                    for (const exercise of studentExam.exercises) {
                        if (individualEndDate.getTime() - Date.now() < 5000) {
                            console.log(`End date is reached`);
                            break endDateLoop;
                        }
                        console.log(`Exercise is of type ${exercise.type}`);
                        let studentParticipations = exercise.studentParticipations;

                        switch (exercise.type) {
                            case 'quiz':
                                submissions = studentParticipations[0].submissions;
                                submissionId = submissions[0].id;
                                submissions[0] = submitRandomAnswerRESTExam(artemis, exercise, 10, submissionId);
                                break;

                            case 'text':
                                submissions = studentParticipations[0].submissions;
                                submissionId = submissions[0].id;
                                submissions[0] = submitRandomTextAnswerExam(artemis, exercise, submissionId);
                                break;

                            case 'modeling':
                                submissions = studentParticipations[0].submissions;
                                submissionId = submissions[0].id;
                                submissions[0] = submitRandomModelingAnswerExam(artemis, exercise, submissionId);
                                break;

                            case 'programming':
                                console.log('Programming submission counter is ' + programmingSubmissionCounter);
                                if (programmingSubmissionCounter === 0) {
                                    let simulation = new ParticipationSimulation(
                                        websocketConnectionTime,
                                        exercise.id,
                                        studentParticipations[0].id,
                                        someSuccessfulErrorContentJava(false),
                                    );
                                    simulateSubmission(artemis, simulation, TestResult.FAIL, '2 of 13 passed');
                                } else if (programmingSubmissionCounter === 1) {
                                    let simulation = new ParticipationSimulation(websocketConnectionTime, exercise.id, studentParticipations[0].id, allSuccessfulContentJava);
                                    simulateSubmission(artemis, simulation, TestResult.SUCCESS);
                                } else if (programmingSubmissionCounter >= 2) {
                                    let simulation = new ParticipationSimulation(websocketConnectionTime, exercise.id, studentParticipations[0].id, buildErrorContentJava);
                                    simulateSubmission(artemis, simulation, TestResult.BUILD_ERROR);
                                }
                                programmingSubmissionCounter++;
                                sleep(40);
                                break;
                        }
                        sleep(10);
                    }
                }

                studentExam.started = true;
                console.log('Submitting exam: ' + JSON.stringify(studentExam));
                submitExam(artemis, data.courseId, data.examId, studentExam);
            }, 1000);
        });

        // console.log('Received EXAM ' + JSON.stringify(studentExam) + ' for user ' + baseUsername.replace('USERID', userId));
    });

    return data;
}

export function teardown(data) {
    if (onlyPrepare) {
        return;
    }
    const instructorUsername = baseUsername.replace('USERID', '101');
    const instructorPassword = basePassword.replace('USERID', '101');

    const artemis = login(instructorUsername, instructorPassword);

    sleep(5);
    evaluateQuizzes(artemis, data.courseId, data.examId);

    const shouldCleanup = __ENV.CLEANUP === true || __ENV.CLEANUP === 'true';
    if (shouldCleanup) {
        const artemis = login(adminUsername, adminPassword);
        const courseId = data.courseId;

        deleteCourse(artemis, courseId);
    }
}
