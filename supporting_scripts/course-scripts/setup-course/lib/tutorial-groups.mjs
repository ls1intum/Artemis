/**
 * Tutorial groups creation with schedule
 */

export async function createTutorialGroups(client, courseId, tutors, students) {
    const tutorialGroups = [];

    // First, create a tutorial groups configuration for the course
    const config = await createTutorialGroupsConfiguration(client, courseId);
    console.log(`    Created tutorial groups configuration`);

    // Create tutorial groups (title max 19 chars)
    const groupConfigs = [
        {
            title: 'Group A - Monday',
            campus: 'Main Campus',
            language: 'English',
            capacity: 20,
            isOnline: false,
            additionalInformation: 'Room 101, Building A',
            tutor: tutors[0],
            dayOfWeek: 1, // Monday
            startTime: '10:00',
            endTime: '12:00',
        },
        {
            title: 'Group B - Wednesday',
            campus: 'Main Campus',
            language: 'English',
            capacity: 20,
            isOnline: false,
            additionalInformation: 'Room 202, Building B',
            tutor: tutors.length > 1 ? tutors[1] : tutors[0],
            dayOfWeek: 3, // Wednesday
            startTime: '14:00',
            endTime: '16:00',
        },
        {
            title: 'Online Group',
            campus: 'Online',
            language: 'English',
            capacity: 30,
            isOnline: true,
            additionalInformation: 'Zoom link will be shared before the session',
            tutor: tutors[0],
            dayOfWeek: 5, // Friday
            startTime: '16:00',
            endTime: '18:00',
        },
    ];

    for (const groupConfig of groupConfigs) {
        const group = await createTutorialGroup(client, courseId, groupConfig);
        tutorialGroups.push(group);

        // Create recurring sessions for the group
        await createTutorialGroupSessions(client, courseId, group.id, groupConfig);
    }

    // Distribute students among groups
    await distributeStudentsToGroups(client, courseId, students, tutorialGroups);

    console.log(`  Created ${tutorialGroups.length} tutorial groups with sessions`);
    return tutorialGroups;
}

async function createTutorialGroupsConfiguration(client, courseId) {
    // Calculate period dates (current semester)
    const now = new Date();
    const periodStart = new Date(now.getFullYear(), now.getMonth(), 1);
    const periodEnd = new Date(now.getFullYear(), now.getMonth() + 4, 0);

    const configuration = {
        course: { id: courseId },
        tutorialPeriodStartInclusive: periodStart.toISOString().split('T')[0],
        tutorialPeriodEndInclusive: periodEnd.toISOString().split('T')[0],
        usePublicTutorialGroupChannels: true,
        useTutorialGroupChannels: true,
    };

    try {
        const response = await client.post(`/api/tutorialgroup/courses/${courseId}/tutorial-groups-configuration`, configuration);
        return response.data;
    } catch (error) {
        // Configuration might already exist
        if (error.response?.status === 400) {
            console.log(`    Tutorial groups configuration already exists`);
            return configuration;
        }
        throw error;
    }
}

async function createTutorialGroup(client, courseId, config) {
    const tutorialGroup = {
        title: config.title,
        campus: config.campus,
        language: config.language,
        capacity: config.capacity,
        isOnline: config.isOnline,
        additionalInformation: config.additionalInformation,
        teachingAssistant: config.tutor ? { login: config.tutor.login } : null,
    };

    const response = await client.post(`/api/tutorialgroup/courses/${courseId}/tutorial-groups`, tutorialGroup);
    console.log(`    Created tutorial group: ${config.title}`);
    return response.data;
}

async function createTutorialGroupSessions(client, courseId, tutorialGroupId, config) {
    // Create 4 sessions (one per week for 4 weeks)
    const now = new Date();

    for (let week = 0; week < 4; week++) {
        // Calculate the date for this session
        const sessionDate = new Date(now);
        sessionDate.setDate(now.getDate() + ((config.dayOfWeek - now.getDay() + 7) % 7) + week * 7);

        // Format date as YYYY-MM-DD
        const dateStr = sessionDate.toISOString().split('T')[0];

        // TutorialGroupSessionRequestDTO expects: date, startTime, endTime, location
        const session = {
            date: dateStr,
            startTime: config.startTime + ':00', // Format as HH:mm:ss
            endTime: config.endTime + ':00',
            location: config.isOnline ? 'Online' : config.additionalInformation,
        };

        try {
            await client.post(`/api/tutorialgroup/courses/${courseId}/tutorial-groups/${tutorialGroupId}/sessions`, session);
        } catch (error) {
            // Sessions might overlap with existing ones, ignore errors
            if (error.response?.status !== 400) {
                console.log(`      Note: Could not create session for week ${week + 1}`);
            }
        }
    }
    console.log(`      Created sessions for ${config.title}`);
}

async function distributeStudentsToGroups(client, courseId, students, groups) {
    if (students.length === 0 || groups.length === 0) return;

    // Distribute students evenly among groups
    for (let i = 0; i < students.length; i++) {
        const student = students[i];
        const group = groups[i % groups.length];

        try {
            await client.post(`/api/tutorialgroup/courses/${courseId}/tutorial-groups/${group.id}/register/${student.login}`);
        } catch (error) {
            // Student might already be registered
            if (error.response?.status !== 400) {
                console.log(`      Note: Could not register ${student.login} to ${group.title}`);
            }
        }
    }
    console.log(`    Distributed ${students.length} students among ${groups.length} groups`);
}
