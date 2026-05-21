/**
 * Main course setup orchestration
 */

import { HttpClient, createMultipartFormData } from './http-client.mjs';
import { authenticate } from './auth.mjs';
import {
    createProgrammingExercises,
    createModelingExercises,
    createTextExercises,
    createQuizExercises,
    createFileUploadExercise,
} from './exercises.mjs';
import { createLectures } from './lectures.mjs';
import { createCompetenciesAndPrerequisites } from './competencies.mjs';
import { createTutorialGroups } from './tutorial-groups.mjs';
import { createFaqs } from './faqs.mjs';
import { createStudentParticipations } from './participations.mjs';
import { updateExerciseDueDate, createAssessments } from './assessments.mjs';
import { createExam } from './exams.mjs';
import { createCommunicationData } from './communication.mjs';

export async function createCourseSetup(config) {
    const client = new HttpClient(config.serverUrl);

    // Step 1: Authenticate as admin
    console.log('[1/13] Authenticating as admin...');
    await authenticate(client, config.adminUser, config.adminPassword);

    // Step 2: Create or get course
    let course;
    if (config.existingCourseId) {
        console.log(`[2/13] Using existing course ID: ${config.existingCourseId}`);
        const courseResponse = await client.get(`/api/core/courses/${config.existingCourseId}`);
        course = courseResponse.data;
    } else {
        console.log('[2/13] Creating course...');
        course = await createCourse(client);
    }
    const courseId = course.id;

    // Step 3: Create users
    console.log('[3/13] Creating users...');
    const { students, tutors, editors, instructors } = await createUsers(client, config.studentCount, config.tutorCount, course, config.studentPassword);

    // Step 4: Create programming exercises
    console.log('[4/13] Creating programming exercises...');
    const programmingExercises = await createProgrammingExercises(client, courseId);

    // Step 5: Create other exercises
    console.log('[5/13] Creating other exercises...');
    const modelingExercises = await createModelingExercises(client, courseId);
    const textExercises = await createTextExercises(client, courseId);
    const quizExercises = await createQuizExercises(client, courseId);
    const fileUploadExercise = await createFileUploadExercise(client, courseId);

    const allExercises = {
        programming: programmingExercises,
        modeling: modelingExercises,
        text: textExercises,
        quiz: quizExercises,
        fileUpload: [fileUploadExercise],
    };

    // Step 6: Create exam with exercises
    console.log('[6/13] Creating exam...');
    const examResult = await createExam(client, courseId, students);

    // Step 7: Create lectures
    console.log('[7/13] Creating lectures...');
    const lectures = await createLectures(client, courseId, allExercises);

    // Step 8: Create competencies and prerequisites
    console.log('[8/13] Creating competencies and prerequisites...');
    await createCompetenciesAndPrerequisites(client, courseId, lectures, allExercises);

    // Step 9: Create tutorial groups
    console.log('[9/13] Creating tutorial groups...');
    await createTutorialGroups(client, courseId, tutors, students);

    // Step 10: Create FAQs
    console.log('[10/13] Creating FAQs...');
    await createFaqs(client, courseId);

    // Step 11: Create communication data
    console.log('[11/13] Creating communication data...');
    const communicationResult = await createCommunicationData(client, courseId, students, config.studentPassword);

    // Step 12: Create participations (student submissions)
    if (!config.skipParticipations) {
        console.log('[12/13] Creating student participations...');
        await createStudentParticipations(client, students, allExercises, config.studentPassword);
    } else {
        console.log('[12/13] Skipping participations (as requested)');
    }

    // Step 13: Create assessments
    if (!config.skipParticipations) {
        console.log('[13/13] Creating assessments...');
        // Update due dates to allow assessment
        const manualExercises = [...modelingExercises, ...textExercises];
        for (const exercise of manualExercises) {
            await updateExerciseDueDate(client, exercise, courseId);
        }
        // Create assessments using tutors/instructors
        const assessors = [...tutors, ...instructors];
        await createAssessments(client, assessors, allExercises, config.studentPassword);
    } else {
        console.log('[13/13] Skipping assessments (as requested)');
    }

    return { courseId, exercises: allExercises, lectures, exam: examResult, communication: communicationResult };
}

async function createCourse(client) {
    const timestamp = Date.now();
    const shortName = `test${timestamp}`;

    const now = new Date();
    const startDate = new Date(now.getTime() - 10 * 60 * 1000); // 10 minutes ago
    const endDate = new Date(now.getTime() + 5 * 60 * 1000); // 5 minutes from now

    const course = {
        title: `Test Course ${new Date().toISOString().split('T')[0]}`,
        shortName,
        startDate: startDate.toISOString(),
        endDate: endDate.toISOString(),
        complaintsEnabled: true,
        requestMoreFeedbackEnabled: true,
        maxComplaints: 3,
        maxTeamComplaints: 3,
        maxComplaintTimeDays: 7,
        maxComplaintTextLimit: 2000,
        maxComplaintResponseTextLimit: 2000,
        maxRequestMoreFeedbackTimeDays: 7,
        courseInformationSharingConfiguration: 'COMMUNICATION_AND_MESSAGING',
        enrollmentEnabled: false,
        accuracyOfScores: 1,
        onlineCourse: false,
        timeZone: 'Europe/Berlin',
    };

    const { body, contentType } = createMultipartFormData({ course });

    const response = await client.post('/api/core/admin/courses', body, {
        headers: { 'Content-Type': contentType },
        contentType: 'multipart',
    });

    const createdCourse = response.data;
    console.log(`  Created course: ${createdCourse.title} (ID: ${createdCourse.id})`);
    console.log(`    Student group: ${createdCourse.studentGroupName}`);
    console.log(`    Tutor group: ${createdCourse.teachingAssistantGroupName}`);
    console.log(`    Editor group: ${createdCourse.editorGroupName}`);
    console.log(`    Instructor group: ${createdCourse.instructorGroupName}`);
    return createdCourse;
}

async function createUsers(client, studentCount, tutorCount, course, password) {
    const students = [];
    const tutors = [];
    const editors = [];
    const instructors = [];
    const courseId = course.id;
    // Use course short name as prefix so users are consistent across script runs
    const prefix = course.shortName.toLowerCase();
    let newUsers = 0;
    let existingUsers = 0;

    // Create students
    for (let i = 1; i <= studentCount; i++) {
        const user = await createUser(client, {
            login: `${prefix}_student_${i}`,
            firstName: 'Student',
            lastName: `User${i}`,
            email: `${prefix}_student${i}@test.local`,
            password,
        });
        students.push(user);
        user.isNew ? newUsers++ : existingUsers++;
        await addUserToCourse(client, courseId, 'students', user.login);
    }

    // Create tutors
    for (let i = 1; i <= tutorCount; i++) {
        const user = await createUser(client, {
            login: `${prefix}_tutor_${i}`,
            firstName: 'Tutor',
            lastName: `User${i}`,
            email: `${prefix}_tutor${i}@test.local`,
            password,
        });
        tutors.push(user);
        user.isNew ? newUsers++ : existingUsers++;
        await addUserToCourse(client, courseId, 'tutors', user.login);
    }

    // Create one editor
    const editor = await createUser(client, {
        login: `${prefix}_editor_1`,
        firstName: 'Editor',
        lastName: 'User1',
        email: `${prefix}_editor1@test.local`,
        password,
    });
    editors.push(editor);
    editor.isNew ? newUsers++ : existingUsers++;
    await addUserToCourse(client, courseId, 'editors', editor.login);

    // Create one instructor
    const instructor = await createUser(client, {
        login: `${prefix}_instructor_1`,
        firstName: 'Instructor',
        lastName: 'User1',
        email: `${prefix}_instructor1@test.local`,
        password,
    });
    instructors.push(instructor);
    instructor.isNew ? newUsers++ : existingUsers++;
    await addUserToCourse(client, courseId, 'instructors', instructor.login);

    const summary = [`${students.length} students`, `${tutors.length} tutors`, `${editors.length} editor`, `${instructors.length} instructor`];
    if (existingUsers > 0) {
        console.log(`  Users: ${summary.join(', ')} (${newUsers} new, ${existingUsers} existing)`);
    } else {
        console.log(`  Created ${summary.join(', ')}`);
    }

    return { students, tutors, editors, instructors };
}

async function createUser(client, userData) {
    const user = {
        activated: true,
        login: userData.login,
        email: userData.email,
        firstName: userData.firstName,
        lastName: userData.lastName,
        langKey: 'en',
        password: userData.password,
    };

    try {
        const response = await client.post('/api/core/admin/users', user);
        return { ...response.data, isNew: true };
    } catch (error) {
        // User might already exist (400 or 409)
        if (error.response?.status === 400 || error.response?.status === 409) {
            // Try to fetch the existing user
            try {
                const existingUserResponse = await client.get(`/api/core/admin/users/${userData.login}`);
                return { ...existingUserResponse.data, isNew: false };
            } catch {
                // If we can't fetch, return minimal user object
                return { login: userData.login, isNew: false };
            }
        }
        throw error;
    }
}

async function addUserToCourse(client, courseId, group, username) {
    try {
        await client.post(`/api/core/courses/${courseId}/${group}/${username}`);
        return true;
    } catch (error) {
        // Ignore if already added (400) or other expected errors
        if (error.response?.status === 400) {
            return false; // Already in course
        }
        throw error;
    }
}
