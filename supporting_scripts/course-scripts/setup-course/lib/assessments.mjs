/**
 * Assessment creation for manual exercises (modeling, text)
 */

import { HttpClient } from './http-client.mjs';
import { authenticate } from './auth.mjs';

/**
 * Update exercise due date to the past to allow assessment
 */
export async function updateExerciseDueDate(client, exercise, courseId) {
    // Set due date to 1 minute ago to allow assessment
    const pastDate = new Date(Date.now() - 60000).toISOString();
    const exerciseType = exercise.type;

    try {
        if (exerciseType === 'modeling') {
            // Modeling exercises use UpdateModelingExerciseDTO with courseId as direct field
            const updateDTO = {
                id: exercise.id,
                title: exercise.title,
                shortName: exercise.shortName,
                maxPoints: exercise.maxPoints,
                dueDate: pastDate,
                includedInOverallScore: exercise.includedInOverallScore || 'INCLUDED_COMPLETELY',
                courseId: courseId,
            };
            await client.put('/api/modeling/modeling-exercises', updateDTO);
        } else if (exerciseType === 'text') {
            // Text exercises use TextExercise entity with nested course object
            // includedInOverallScore is required by validateScoreSettings()
            const updateDTO = {
                id: exercise.id,
                type: 'text',
                title: exercise.title,
                shortName: exercise.shortName,
                maxPoints: exercise.maxPoints,
                dueDate: pastDate,
                includedInOverallScore: exercise.includedInOverallScore || 'INCLUDED_COMPLETELY',
                course: { id: courseId },
            };
            await client.put('/api/text/text-exercises', updateDTO);
        } else {
            return;
        }

        console.log(`    Updated due date for: ${exercise.title}`);
    } catch (error) {
        const detail = error.response?.data?.detail || error.response?.data?.message || error.message;
        console.log(`    Could not update due date for ${exercise.title}: ${detail}`);
    }
}

/**
 * Create assessments for all manual exercises
 */
export async function createAssessments(client, assessors, exercises, studentPassword) {
    // Assess modeling exercises
    for (const exercise of exercises.modeling || []) {
        await assessExerciseSubmissions(client, assessors, exercise, 'modeling', studentPassword);
    }

    // Assess text exercises
    for (const exercise of exercises.text || []) {
        await assessExerciseSubmissions(client, assessors, exercise, 'text', studentPassword);
    }
}

/**
 * Assess all submissions for a single exercise
 */
async function assessExerciseSubmissions(client, assessors, exercise, exerciseType, studentPassword) {
    if (!assessors || assessors.length === 0) {
        console.log(`    No assessors available for ${exercise.title}`);
        return;
    }

    // Try each assessor until one works
    for (const assessor of assessors) {
        const assessorClient = new HttpClient(client.baseUrl);
        try {
            await authenticate(assessorClient, assessor.login, studentPassword, true);

            // Keep assessing until no more submissions
            let assessedCount = 0;
            while (true) {
                const assessed = await createManualAssessment(assessorClient, exercise, exerciseType, assessor.login);
                if (!assessed) break;
                assessedCount++;
            }

            if (assessedCount > 0) {
                console.log(`    ${assessor.login}: Assessed ${assessedCount} submissions for ${exercise.title}`);
            }
            return; // Success with this assessor
        } catch (error) {
            // Try next assessor
        }
    }
    console.log(`    Could not create assessments for ${exercise.title}`);
}

/**
 * Create a manual assessment for a single submission
 * Returns true if assessment was created, false if no more submissions
 */
async function createManualAssessment(client, exercise, exerciseType, assessorLogin) {
    // Get and lock an unassessed submission
    let lockUrl;
    switch (exerciseType) {
        case 'modeling':
            lockUrl = `/api/modeling/exercises/${exercise.id}/modeling-submission-without-assessment?lock=true&correction-round=0`;
            break;
        case 'text':
            lockUrl = `/api/text/exercises/${exercise.id}/text-submission-without-assessment?lock=true&correction-round=0`;
            break;
        default:
            return false;
    }

    // Lock a submission for assessment
    let lockResponse;
    try {
        lockResponse = await client.get(lockUrl);
    } catch (lockError) {
        const status = lockError.response?.status;
        if (status === 403) {
            // Auth error - throw so caller can try another assessor
            throw lockError;
        }
        if (status === 404) {
            // No more unassessed submissions
            return false;
        }
        console.log(`      Lock failed (${status}): ${lockError.response?.data?.detail || lockError.message}`);
        return false;
    }

    const submission = lockResponse.data;
    if (!submission) {
        return false;
    }

    // Get the result ID from the locked submission
    const results = submission.results || [];
    const result = results[0];
    if (!result || !result.id) {
        console.log(`      No result in locked submission`);
        return false;
    }

    // Create feedback and submit the assessment
    const score = 70 + Math.floor(Math.random() * 31); // 70-100
    const assessment = {
        feedbacks: [
            {
                credits: (score / 100) * exercise.maxPoints,
                detailText: 'Good work! Here are some suggestions for improvement.',
                type: 'MANUAL',
                positive: score >= 80,
            },
        ],
        assessmentNote: null,
    };

    // Submit the assessment
    let assessmentUrl;
    let method = 'put';
    switch (exerciseType) {
        case 'modeling':
            assessmentUrl = `/api/modeling/modeling-submissions/${submission.id}/result/${result.id}/assessment?submit=true`;
            break;
        case 'text':
            assessmentUrl = `/api/text/participations/${submission.participation?.id}/results/${result.id}/submit-text-assessment`;
            method = 'post';
            break;
    }

    try {
        if (method === 'post') {
            await client.post(assessmentUrl, assessment);
        } else {
            await client.put(assessmentUrl, assessment);
        }
        console.log(`      ${assessorLogin}: Assessed submission with ${score}%`);
        return true;
    } catch (error) {
        const status = error.response?.status;
        if (status === 403) {
            throw error;
        }
        const detail = error.response?.data?.detail || error.response?.data?.message || error.message;
        console.log(`      ${assessorLogin}: Assessment failed (${status}): ${detail}`);
        return false;
    }
}
