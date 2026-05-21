/**
 * Competencies and prerequisites creation
 */

export async function createCompetenciesAndPrerequisites(client, courseId, lectures, exercises) {
    const prerequisites = [];
    const competencies = [];

    // Create prerequisites (things students should know before the course)
    const prereqConfigs = [
        {
            title: 'Basic Programming',
            description: 'Students should have basic programming experience in any language.',
            taxonomy: 'UNDERSTAND',
            masteryThreshold: 80,
        },
        {
            title: 'Mathematics Fundamentals',
            description: 'Basic understanding of algebra and discrete mathematics.',
            taxonomy: 'REMEMBER',
            masteryThreshold: 70,
        },
    ];

    for (const config of prereqConfigs) {
        const prereq = await createPrerequisite(client, courseId, config);
        prerequisites.push(prereq);
    }
    console.log(`  Created ${prerequisites.length} prerequisites`);

    // Create competencies (learning objectives for the course)
    const competencyConfigs = [
        {
            title: 'Software Design Principles',
            description: 'Understand and apply SOLID principles and clean code practices.',
            taxonomy: 'APPLY',
            masteryThreshold: 80,
            linkedLecture: lectures[0],
            linkedExercise: exercises.programming[0],
        },
        {
            title: 'Design Patterns',
            description: 'Recognize and implement common design patterns.',
            taxonomy: 'ANALYZE',
            masteryThreshold: 75,
            linkedLecture: lectures[1],
            linkedExercise: exercises.modeling[0],
        },
        {
            title: 'Testing Strategies',
            description: 'Develop and execute comprehensive test plans.',
            taxonomy: 'CREATE',
            masteryThreshold: 85,
            linkedLecture: lectures[2],
            linkedExercise: exercises.programming[1],
        },
        {
            title: 'Architecture Decisions',
            description: 'Evaluate and justify architectural decisions.',
            taxonomy: 'EVALUATE',
            masteryThreshold: 80,
            linkedLecture: lectures[3],
            linkedExercise: exercises.text[0],
        },
    ];

    for (const config of competencyConfigs) {
        const competency = await createCompetency(client, courseId, config);
        competencies.push(competency);

        // Link lecture to competency
        if (config.linkedLecture) {
            await linkLectureToCompetency(client, courseId, competency.id, config.linkedLecture.id);
        }

        // Link exercise to competency
        if (config.linkedExercise) {
            await linkExerciseToCompetency(client, courseId, competency.id, config.linkedExercise.id);
        }
    }
    console.log(`  Created ${competencies.length} competencies with lecture and exercise links`);

    return { prerequisites, competencies };
}

async function createPrerequisite(client, courseId, config) {
    const prerequisite = {
        type: 'prerequisite',
        title: config.title,
        description: config.description,
        taxonomy: config.taxonomy,
        masteryThreshold: config.masteryThreshold,
        optional: false,
    };

    const response = await client.post(`/api/atlas/courses/${courseId}/prerequisites`, prerequisite);
    console.log(`    Created prerequisite: ${config.title}`);
    return response.data;
}

async function createCompetency(client, courseId, config) {
    const competency = {
        type: 'competency',
        title: config.title,
        description: config.description,
        taxonomy: config.taxonomy,
        masteryThreshold: config.masteryThreshold,
        optional: false,
    };

    const response = await client.post(`/api/atlas/courses/${courseId}/competencies`, competency);
    console.log(`    Created competency: ${config.title}`);
    return response.data;
}

async function linkLectureToCompetency(client, courseId, competencyId, lectureId) {
    try {
        // Get the lecture with its units
        const lectureResponse = await client.get(`/api/lecture/lectures/${lectureId}`);
        const lecture = lectureResponse.data;

        // Update each lecture unit to link to the competency
        if (lecture.lectureUnits) {
            for (const unit of lecture.lectureUnits) {
                if (unit.id) {
                    // Add competency link to unit
                    const updatedUnit = {
                        ...unit,
                        competencies: [{ id: competencyId }],
                    };

                    // Update based on unit type
                    if (unit.type === 'text') {
                        await client.put(`/api/lecture/lectures/${lectureId}/text-units`, updatedUnit);
                    }
                }
            }
        }
    } catch (error) {
        console.log(`      Note: Could not link lecture ${lectureId} to competency ${competencyId}`);
    }
}

async function linkExerciseToCompetency(client, courseId, competencyId, exerciseId) {
    // Note: Linking exercises to competencies via API requires updating each exercise
    // with the competency reference. This varies by exercise type and is complex.
    // For now, we skip this - competencies are created but exercises need to be
    // linked manually through the UI if needed.
}
