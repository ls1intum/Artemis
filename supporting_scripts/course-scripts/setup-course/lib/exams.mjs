/**
 * Exam creation and setup
 */

import { createMultipartFormData } from './http-client.mjs';

const timestamp = Date.now();

/**
 * Create a complete exam with exercises, student registration, and preparation
 */
export async function createExam(client, courseId, students) {
    // Step 1: Create the exam
    console.log('  Creating exam...');
    const exam = await createExamEntity(client, courseId);

    // Step 2: Create exercise groups and add exercises
    console.log('  Creating exercise groups and exercises...');
    const exerciseGroups = await createExerciseGroups(client, courseId, exam.id);

    // Step 3: Register all students for the exam
    console.log('  Registering students...');
    await registerStudentsForExam(client, courseId, exam.id, students);

    // Step 4: Generate student exams
    console.log('  Generating student exams...');
    const studentExams = await generateStudentExams(client, courseId, exam.id);

    // Step 5: Prepare exercise start (create participations)
    console.log('  Preparing exercise start...');
    await prepareExerciseStart(client, courseId, exam.id);

    console.log(`  Created exam: ${exam.title} with ${exerciseGroups.length} exercise groups`);
    console.log(`  Registered ${students.length} students, generated ${studentExams.length} student exams`);

    return { exam, exerciseGroups, studentExams };
}

/**
 * Create the exam entity
 */
async function createExamEntity(client, courseId) {
    const now = new Date();
    const startDate = new Date(now.getTime() + 5 * 60 * 1000); // Start in 5 minutes
    const endDate = new Date(startDate.getTime() + 90 * 60 * 1000); // 90 minute exam
    const workingTime = 90 * 60; // 90 minutes in seconds

    const exam = {
        title: `Programming Exam ${new Date().toISOString().split('T')[0]}`,
        testExam: false,
        visibleDate: now.toISOString(),
        startDate: startDate.toISOString(),
        endDate: endDate.toISOString(),
        publishResultsDate: new Date(endDate.getTime() + 60 * 60 * 1000).toISOString(), // 1 hour after end
        gracePeriod: 180, // 3 minutes grace period
        workingTime: workingTime,
        startText: 'Welcome to the exam. Read all instructions carefully before starting.',
        endText: 'Thank you for completing the exam. Your submission has been recorded.',
        examMaxPoints: 100,
        randomizeExerciseOrder: false,
        numberOfExercisesInExam: 2, // Programming + Quiz
        numberOfCorrectionRoundsInExam: 1,
        course: { id: courseId },
        exerciseGroups: [],
    };

    const response = await client.post(`/api/exam/courses/${courseId}/exams`, exam);
    console.log(`    Created exam: ${response.data.title} (ID: ${response.data.id})`);
    return response.data;
}

/**
 * Create exercise groups with programming and quiz exercises
 */
async function createExerciseGroups(client, courseId, examId) {
    const exerciseGroups = [];

    // Create programming exercise group
    const programmingGroup = await createExerciseGroup(client, courseId, examId, {
        title: 'Programming Exercise',
        isMandatory: true,
    });

    // Add programming exercise to the group
    const programmingExercise = await createExamProgrammingExercise(client, courseId, programmingGroup.id);
    programmingGroup.exercises = [programmingExercise];
    exerciseGroups.push(programmingGroup);

    // Create quiz exercise group
    const quizGroup = await createExerciseGroup(client, courseId, examId, {
        title: 'Quiz Exercise',
        isMandatory: true,
    });

    // Add quiz exercise to the group
    const quizExercise = await createExamQuizExercise(client, courseId, quizGroup.id);
    quizGroup.exercises = [quizExercise];
    exerciseGroups.push(quizGroup);

    return exerciseGroups;
}

/**
 * Create a single exercise group
 */
async function createExerciseGroup(client, courseId, examId, config) {
    const exerciseGroup = {
        title: config.title,
        isMandatory: config.isMandatory,
        exam: { id: examId },
    };

    const response = await client.post(
        `/api/exam/courses/${courseId}/exams/${examId}/exercise-groups`,
        exerciseGroup
    );
    console.log(`    Created exercise group: ${config.title}`);
    return response.data;
}

/**
 * Create a programming exercise for an exam
 */
async function createExamProgrammingExercise(client, courseId, exerciseGroupId) {
    const exercise = {
        type: 'programming',
        title: 'Exam - Java Algorithms',
        shortName: `ExamJava${timestamp}`,
        exerciseGroup: { id: exerciseGroupId },
        programmingLanguage: 'JAVA',
        projectType: 'PLAIN_GRADLE',
        allowOnlineEditor: true,
        allowOfflineIde: false,
        maxPoints: 50,
        assessmentType: 'AUTOMATIC',
        packageName: 'de.tum.in.ase.exam',
        staticCodeAnalysisEnabled: false,
        sequentialTestRuns: false,
        problemStatement: `# Java Algorithms Exam

## Task Description
Implement the required algorithm according to the specifications below.

## Requirements
1. Implement the \`solve()\` method in the \`Algorithm\` class
2. Your solution must handle all edge cases
3. Optimize for both time and space complexity

## Grading Criteria
- Correctness: 30 points
- Edge cases: 10 points
- Code quality: 10 points

## Hints
- Read the test cases carefully
- Consider the time limit for your solution`,
        buildConfig: {
            buildScript: `#!/usr/bin/env bash
set -e
gradle () {
  echo '⚙️ executing gradle'
  chmod +x ./gradlew
  ./gradlew clean test
}
main () {
  gradle
}
main "\${@}"
`,
            checkoutSolutionRepository: false,
        },
    };

    const response = await client.post('/api/programming/programming-exercises/setup', exercise);
    console.log(`    Created programming exercise: ${exercise.title}`);
    return response.data;
}

/**
 * Create a quiz exercise for an exam
 */
async function createExamQuizExercise(client, courseId, exerciseGroupId) {
    // Quiz exercise points are calculated from sum of question points
    // Total should be 50 points to match the exam configuration
    const quizQuestions = [
        {
            type: 'multiple-choice',
            title: 'Java Basics',
            text: 'Which of the following are primitive data types in Java?',
            points: 15,
            singleChoice: false,
            scoringType: 'ALL_OR_NOTHING',
            answerOptions: [
                { text: 'int', isCorrect: true, invalid: false },
                { text: 'String', isCorrect: false, invalid: false },
                { text: 'boolean', isCorrect: true, invalid: false },
                { text: 'ArrayList', isCorrect: false, invalid: false },
            ],
            invalid: false,
            randomizeOrder: false,
        },
        {
            type: 'multiple-choice',
            title: 'Object-Oriented Programming',
            text: 'What is the purpose of the "final" keyword when applied to a class in Java?',
            points: 15,
            singleChoice: true,
            scoringType: 'ALL_OR_NOTHING',
            answerOptions: [
                { text: 'The class cannot be instantiated', isCorrect: false, invalid: false },
                { text: 'The class cannot be inherited', isCorrect: true, invalid: false },
                { text: 'The class cannot have methods', isCorrect: false, invalid: false },
                { text: 'The class must be abstract', isCorrect: false, invalid: false },
            ],
            invalid: false,
            randomizeOrder: false,
        },
        {
            type: 'multiple-choice',
            title: 'Exception Handling',
            text: 'Which keyword is used to throw an exception in Java?',
            points: 20,
            singleChoice: true,
            scoringType: 'ALL_OR_NOTHING',
            answerOptions: [
                { text: 'try', isCorrect: false, invalid: false },
                { text: 'catch', isCorrect: false, invalid: false },
                { text: 'throw', isCorrect: true, invalid: false },
                { text: 'finally', isCorrect: false, invalid: false },
            ],
            invalid: false,
            randomizeOrder: false,
        },
    ];

    // Note: maxPoints for quiz exercises is derived from the sum of question points (15+15+20=50)
    const exercise = {
        type: 'quiz',
        title: 'Exam - Java Knowledge Quiz',
        shortName: `ExamQuiz${timestamp}`,
        quizQuestions,
        duration: 600, // 10 minutes for the quiz portion
        randomizeQuestionOrder: false,
        quizMode: 'SYNCHRONIZED',
        mode: 'INDIVIDUAL',
        includedInOverallScore: 'INCLUDED_COMPLETELY',
    };

    // Use the exam-specific endpoint for quiz exercises
    const { body, contentType } = createMultipartFormData({ exercise });
    const response = await client.post(`/api/quiz/exercise-groups/${exerciseGroupId}/quiz-exercises`, body, {
        headers: { 'Content-Type': contentType },
        contentType: 'multipart',
    });
    console.log(`    Created quiz exercise: ${exercise.title}`);
    return response.data;
}

/**
 * Register all students for the exam
 */
async function registerStudentsForExam(client, courseId, examId, students) {
    // Register students one by one using the single student endpoint
    let registeredCount = 0;
    for (const student of students) {
        try {
            await client.post(`/api/exam/courses/${courseId}/exams/${examId}/students/${student.login}`);
            registeredCount++;
        } catch (error) {
            // Student might already be registered
            if (error.response?.status !== 400) {
                console.log(`    Warning: Could not register ${student.login}: ${error.response?.status}`);
            }
        }
    }
    console.log(`    Registered ${registeredCount} students for exam`);
}

/**
 * Generate student exams for all registered students
 */
async function generateStudentExams(client, courseId, examId) {
    try {
        const response = await client.post(
            `/api/exam/courses/${courseId}/exams/${examId}/generate-student-exams`
        );
        console.log(`    Generated ${response.data.length} student exams`);
        return response.data;
    } catch (error) {
        const errorData = error.response?.data;
        const errorMessage = errorData?.message || errorData?.title || errorData?.detail || error.message;
        const errorKey = errorData?.errorKey || '';
        console.log(`    Could not generate student exams: ${errorMessage}${errorKey ? ` (${errorKey})` : ''}`);
        if (errorData && typeof errorData === 'object') {
            console.log(`    Error details: ${JSON.stringify(errorData)}`);
        }
        return [];
    }
}

/**
 * Prepare exercise start - creates participations for all exercises
 */
async function prepareExerciseStart(client, courseId, examId) {
    try {
        await client.post(`/api/exam/courses/${courseId}/exams/${examId}/student-exams/start-exercises`);
        console.log('    Exercise start preparation initiated');

        // Wait a bit and check status
        await new Promise((resolve) => setTimeout(resolve, 2000));

        try {
            const statusResponse = await client.get(
                `/api/exam/courses/${courseId}/exams/${examId}/student-exams/start-exercises/status`
            );
            if (statusResponse.data) {
                const status = statusResponse.data;
                console.log(`    Preparation status: ${status.finished || 0} finished, ${status.failed || 0} failed`);
            }
        } catch {
            // Status endpoint might not return data immediately
        }
    } catch (error) {
        console.log(`    Could not prepare exercise start: ${error.response?.data?.message || error.message}`);
    }
}
