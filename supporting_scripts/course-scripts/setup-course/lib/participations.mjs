/**
 * Student participations and submissions
 */

import { HttpClient } from './http-client.mjs';
import { authenticate } from './auth.mjs';

/**
 * Create participations and submissions for all students
 */
export async function createStudentParticipations(client, students, exercises, studentPassword) {
    for (const student of students) {
        await createStudentSubmissions(client, student, exercises, studentPassword);
    }
    console.log(`  Created participations for ${students.length} students`);
}

async function createStudentSubmissions(client, student, exercises, studentPassword) {
    // Authenticate as student for participations
    const studentClient = new HttpClient(client.baseUrl);
    try {
        await authenticate(studentClient, student.login, studentPassword, true);
    } catch (error) {
        console.log(`    Could not authenticate as ${student.login}, skipping submissions`);
        return;
    }

    // Log exercise counts for debugging
    const programmingCount = exercises.programming?.length || 0;
    const modelingCount = exercises.modeling?.length || 0;
    const textCount = exercises.text?.length || 0;
    const quizCount = exercises.quiz?.length || 0;
    const fileUploadCount = exercises.fileUpload?.length || 0;

    console.log(`    ${student.login}: Submitting to ${programmingCount} programming, ${modelingCount} modeling, ${textCount} text, ${quizCount} quiz exercises`);

    // Participate in all programming exercises
    for (const exercise of exercises.programming || []) {
        await participateInProgrammingExercise(studentClient, exercise, student);
    }

    // Submit to modeling exercises
    for (const exercise of exercises.modeling || []) {
        await submitModelingExercise(studentClient, exercise, student);
    }

    // Submit to text exercises
    for (const exercise of exercises.text || []) {
        await submitTextExercise(studentClient, exercise, student);
    }

    // Participate in quiz exercises
    for (const exercise of exercises.quiz || []) {
        await participateInQuizExercise(studentClient, exercise, student);
    }

    // Participate in file upload exercises
    for (const exercise of exercises.fileUpload || []) {
        await participateInFileUploadExercise(studentClient, exercise, student);
    }
}

async function participateInProgrammingExercise(client, exercise, student) {
    try {
        // Start participation (general endpoint works for all exercise types)
        const participationResponse = await client.post(
            `/api/exercise/exercises/${exercise.id}/participations`
        );
        const participation = participationResponse.data;
        console.log(`      ${student.login}: Started programming exercise ${exercise.title}`);

        // Trigger a build to create a submission with the template code
        try {
            await client.post(`/api/programming/programming-submissions/${participation.id}/trigger-build?submissionType=MANUAL`);
            console.log(`      ${student.login}: Triggered build for ${exercise.title}`);
        } catch (buildError) {
            // Build might fail if CI is not configured, that's ok
            console.log(`      ${student.login}: Build trigger note: ${buildError.response?.status || buildError.message}`);
        }

        return participation;
    } catch (error) {
        if (error.response?.status !== 400) {
            console.log(`      Note: ${student.login} could not start ${exercise.title}`);
        }
        return null;
    }
}

async function submitModelingExercise(studentClient, exercise, student) {
    try {
        // Start participation
        const participationResponse = await studentClient.post(
            `/api/exercise/exercises/${exercise.id}/participations`
        );
        const participation = participationResponse.data;

        // Submit a model
        const model = createSampleModel(exercise.diagramType);
        const submission = {
            submissionExerciseType: 'modeling',
            model: JSON.stringify(model),
            submitted: true,
            participation: { id: participation.id, type: 'student' },
        };

        await studentClient.put(`/api/modeling/exercises/${exercise.id}/modeling-submissions`, submission);
        console.log(`      ${student.login}: Submitted ${exercise.title}`);
        return participation;
    } catch (error) {
        if (error.response?.status !== 400) {
            console.log(`      ${student.login}: Modeling error: ${error.response?.data?.message || error.message}`);
        }
        return null;
    }
}

async function submitTextExercise(studentClient, exercise, student) {
    try {
        // Start participation
        const participationResponse = await studentClient.post(
            `/api/exercise/exercises/${exercise.id}/participations`
        );
        const participation = participationResponse.data;

        // Submit text
        const submission = {
            submissionExerciseType: 'text',
            text: generateSampleText(exercise.title),
            submitted: true,
            participation: { id: participation.id, type: 'student' },
        };

        await studentClient.put(`/api/text/exercises/${exercise.id}/text-submissions`, submission);
        console.log(`      ${student.login}: Submitted ${exercise.title}`);
        return participation;
    } catch (error) {
        if (error.response?.status !== 400) {
            console.log(`      ${student.login}: Text error: ${error.response?.data?.message || error.message}`);
        }
        return null;
    }
}

async function participateInQuizExercise(client, exercise, student) {
    try {
        // For quiz exercises, we need to submit answers during the quiz period
        // Check if quiz is started
        const quizResponse = await client.get(`/api/quiz/quiz-exercises/${exercise.id}/for-student`);
        const quiz = quizResponse.data;

        if (!quiz.quizStarted) {
            console.log(`      ${student.login}: Quiz ${exercise.title} not yet started`);
            return null;
        }

        if (quiz.quizEnded) {
            console.log(`      ${student.login}: Quiz ${exercise.title} already ended`);
            return null;
        }

        console.log(`      ${student.login}: Quiz ${exercise.title} is active, joining and submitting...`);

        // Join the quiz batch (only for BATCHED and INDIVIDUAL modes, not SYNCHRONIZED)
        // SYNCHRONIZED mode quizzes don't support batch joining - all students participate at once
        if (quiz.quizMode !== 'SYNCHRONIZED') {
            try {
                await client.post(`/api/quiz/quiz-exercises/${exercise.id}/join`, { password: null });
            } catch (joinError) {
                // May already be joined, continue
            }
        }

        // Start participation to get submission
        const participationResponse = await client.post(`/api/quiz/quiz-exercises/${exercise.id}/start-participation`);
        const participation = participationResponse.data;

        // Submit quiz answers using the submission from start-participation
        const submission = {
            submissionExerciseType: 'quiz',
            submitted: true,
            submittedAnswers: createQuizAnswers(quiz.quizQuestions),
        };

        // Save and submit the quiz answers
        await client.post(`/api/quiz/exercises/${exercise.id}/submissions/live?submit=true`, submission);
        console.log(`      ${student.login}: Submitted quiz ${exercise.title}`);

        return participation;
    } catch (error) {
        const status = error.response?.status;
        const data = error.response?.data;
        console.log(`      ${student.login}: Quiz error (${status}):`, JSON.stringify(data, null, 2));
        return null;
    }
}

async function participateInFileUploadExercise(studentClient, exercise, student) {
    try {
        // Start participation (general endpoint works for all exercise types)
        const participationResponse = await studentClient.post(
            `/api/exercise/exercises/${exercise.id}/participations`
        );
        const participation = participationResponse.data;

        // Note: File upload exercises require actual file upload via multipart form data.
        // This is complex to implement in a script, so we just start the participation.
        // No assessment is created since there's no submission to assess.
        console.log(`      ${student.login}: Started file upload exercise ${exercise.title} (no file submitted)`);

        return participation;
    } catch (error) {
        if (error.response?.status !== 400) {
            console.log(`      Note: ${student.login} could not start ${exercise.title}`);
        }
        return null;
    }
}

function createSampleModel(diagramType) {
    // Create a simple model based on diagram type
    const baseModel = {
        version: '2.0.0',
        type: diagramType,
        size: { width: 800, height: 600 },
        interactive: { elements: [], relationships: [] },
        elements: [],
        relationships: [],
        assessments: [],
    };

    if (diagramType === 'ClassDiagram') {
        baseModel.elements = [
            {
                id: 'class-1',
                type: 'Class',
                name: 'Student',
                bounds: { x: 100, y: 100, width: 200, height: 150 },
                attributes: ['+name: String', '+id: int'],
                methods: ['+enroll(): void'],
            },
            {
                id: 'class-2',
                type: 'Class',
                name: 'Course',
                bounds: { x: 400, y: 100, width: 200, height: 150 },
                attributes: ['+title: String', '+credits: int'],
                methods: ['+addStudent(s: Student): void'],
            },
        ];
        baseModel.relationships = [
            {
                id: 'rel-1',
                type: 'ClassBidirectional',
                source: { element: 'class-1' },
                target: { element: 'class-2' },
            },
        ];
    } else if (diagramType === 'ActivityDiagram') {
        baseModel.elements = [
            {
                id: 'start',
                type: 'ActivityInitialNode',
                bounds: { x: 100, y: 50, width: 30, height: 30 },
            },
            {
                id: 'action-1',
                type: 'ActivityActionNode',
                name: 'Start Exercise',
                bounds: { x: 70, y: 120, width: 120, height: 50 },
            },
            {
                id: 'action-2',
                type: 'ActivityActionNode',
                name: 'Submit Solution',
                bounds: { x: 70, y: 200, width: 120, height: 50 },
            },
            {
                id: 'end',
                type: 'ActivityFinalNode',
                bounds: { x: 100, y: 290, width: 30, height: 30 },
            },
        ];
        baseModel.relationships = [
            {
                id: 'flow-1',
                type: 'ActivityControlFlow',
                source: { element: 'start' },
                target: { element: 'action-1' },
            },
            {
                id: 'flow-2',
                type: 'ActivityControlFlow',
                source: { element: 'action-1' },
                target: { element: 'action-2' },
            },
            {
                id: 'flow-3',
                type: 'ActivityControlFlow',
                source: { element: 'action-2' },
                target: { element: 'end' },
            },
        ];
    }

    return baseModel;
}

function generateSampleText(exerciseTitle) {
    const texts = {
        'Essay: Software Architecture': `
# Software Architecture Analysis

## Introduction
Software architecture defines the high-level structure of a software system. It encompasses the major components, their relationships, and the principles governing their design and evolution.

## Key Architectural Patterns

### Layered Architecture
The layered pattern organizes code into horizontal layers, each with a specific responsibility. Common layers include presentation, business logic, and data access.

### Microservices
This pattern structures an application as a collection of loosely coupled services. Each service is independently deployable and scalable.

### Event-Driven Architecture
Systems communicate through events, enabling loose coupling and high scalability. Components react to events asynchronously.

## Conclusion
Choosing the right architecture depends on system requirements, team expertise, and scalability needs. A well-designed architecture facilitates maintainability and evolution.
`,
        'Summary: Design Patterns': `
# Design Patterns Summary

Design patterns are reusable solutions to common software design problems.

## Creational Patterns
- **Singleton**: Ensures only one instance of a class exists
- **Factory Method**: Creates objects without specifying exact class
- **Builder**: Constructs complex objects step by step

## Structural Patterns
- **Adapter**: Allows incompatible interfaces to work together
- **Decorator**: Adds behavior to objects dynamically
- **Facade**: Provides simplified interface to complex subsystems

## Behavioral Patterns
- **Observer**: Defines one-to-many dependency between objects
- **Strategy**: Enables selecting algorithms at runtime
- **Command**: Encapsulates requests as objects

These patterns improve code reusability and maintainability.
`,
    };

    return (
        texts[exerciseTitle] ||
        `
# Response to ${exerciseTitle}

## Overview
This is a comprehensive response to the exercise requirements.

## Main Points
1. Understanding the core concepts
2. Applying theoretical knowledge
3. Providing practical examples

## Conclusion
The concepts discussed are fundamental to software engineering practice.
`
    );
}

function createQuizAnswers(questions) {
    if (!questions) return [];

    return questions.map((q) => {
        if (q.type === 'multiple-choice') {
            // Select correct answers (or random if we don't know)
            const selectedOptions = q.answerOptions
                ?.filter((opt) => opt.isCorrect)
                .map((opt) => ({ id: opt.id })) || [{ id: q.answerOptions?.[0]?.id }];

            return {
                quizQuestion: { id: q.id, type: 'multiple-choice' },
                selectedOptions,
                type: 'multiple-choice',
            };
        } else if (q.type === 'short-answer') {
            return {
                quizQuestion: { id: q.id, type: 'short-answer' },
                text: q.solutions?.[0]?.text || 'answer',
                type: 'short-answer',
            };
        } else if (q.type === 'drag-and-drop') {
            // Create mappings based on correct answers
            const mappings =
                q.correctMappings?.map((m) => ({
                    dragItem: { id: q.dragItems?.[m.dragItemIndex]?.id },
                    dropLocation: { id: q.dropLocations?.[m.dropLocationIndex]?.id },
                })) || [];

            return {
                quizQuestion: { id: q.id, type: 'drag-and-drop' },
                mappings,
                type: 'drag-and-drop',
            };
        }
        return { quizQuestion: { id: q.id, type: q.type } };
    });
}
