/**
 * Known seed entity references from Liquibase E2E seed data.
 * These entities are pre-created by the changelog at
 * src/main/resources/config/liquibase/changelog/20260304120000_e2e_seed_data.xml
 * and are available when SPRING_LIQUIBASE_CONTEXTS includes "e2e".
 *
 * All IDs use high ranges (9000+) to avoid conflicts with runtime-created data.
 */

interface SeedCourse {
    id: number;
    shortName: string;
    title: string;
    studentGroup: string;
    tutorGroup: string;
    instructorGroup: string;
}

function makeSeedCourse(id: number, shortName: string, title: string): SeedCourse {
    return {
        id,
        shortName,
        title,
        studentGroup: `artemis-${shortName}-students`,
        tutorGroup: `artemis-${shortName}-tutors`,
        instructorGroup: `artemis-${shortName}-instructors`,
    };
}

export const SEED_COURSES = {
    channel1: makeSeedCourse(9001, 'e2echannel1', 'E2E Channel Test Course 1'),
    channel2: makeSeedCourse(9002, 'e2echannel2', 'E2E Channel Test Course 2'),
    groupChat1: makeSeedCourse(9003, 'e2egroupchat1', 'E2E Group Chat Test Course 1'),
    groupChat2: makeSeedCourse(9004, 'e2egroupchat2', 'E2E Group Chat Test Course 2'),
    exercise: makeSeedCourse(9005, 'e2eexercise', 'E2E Exercise Course'),
    examAssessment: makeSeedCourse(9006, 'e2eexamassess', 'E2E Exam Assessment Course'),
    examResults: makeSeedCourse(9007, 'e2eexamresult', 'E2E Exam Results Course'),
    examParticipation: makeSeedCourse(9008, 'e2eexampart', 'E2E Exam Participation Course'),
    examTestRun: makeSeedCourse(9009, 'e2eexamrun', 'E2E Exam Test Run Course'),
    textAssessment: makeSeedCourse(9010, 'e2etextassess', 'E2E Text Assessment Course'),
    programmingParticipation: makeSeedCourse(9011, 'e2eprogpart', 'E2E Programming Participation Course'),
    quizParticipation: makeSeedCourse(9012, 'e2equizpart', 'E2E Quiz Participation Course'),
    import: makeSeedCourse(9013, 'e2eimport', 'E2E Import Course'),
    programmingManagement: makeSeedCourse(9014, 'e2eprogmgmt', 'E2E Programming Management Course'),
    general: makeSeedCourse(9015, 'e2egeneral', 'E2E General Course'),
    quizAssessment: makeSeedCourse(9016, 'e2equizassess', 'E2E Quiz Assessment Course'),
    exerciseManagement: makeSeedCourse(9025, 'e2eexercisemgmt', 'E2E Exercise Management Course'),
    exerciseParticipation: makeSeedCourse(9018, 'e2eexercisepart', 'E2E Exercise Participation Course'),
    exerciseAssessment: makeSeedCourse(9019, 'e2eexerciseassess', 'E2E Exercise Assessment Course'),
    atlas1: makeSeedCourse(9020, 'e2eatlas1', 'E2E Atlas Course 1'),
    atlas2: makeSeedCourse(9021, 'e2eatlas2', 'E2E Atlas Course 2'),
    lectureManagement: makeSeedCourse(9022, 'e2electuremgmt', 'E2E Lecture Management Course'),
    examManagement: makeSeedCourse(9023, 'e2eexammgmt', 'E2E Exam Management Course'),
    testExam: makeSeedCourse(9024, 'e2etestexam', 'E2E Test Exam Course'),
} as const;

/**
 * Default channel IDs per course. Each course has 4 default channels
 * created in sequence: announcement, organization, random, tech-support.
 * First course (9001) starts at conversation ID 90001.
 */
function courseChannelIds(courseIndex: number) {
    const base = 90001 + (courseIndex - 1) * 4;
    return {
        announcement: base,
        organization: base + 1,
        random: base + 2,
        techSupport: base + 3,
    };
}

export const SEED_CHANNELS = {
    channel1: courseChannelIds(1),
    channel2: courseChannelIds(2),
    groupChat1: courseChannelIds(3),
    groupChat2: courseChannelIds(4),
    exercise: courseChannelIds(5),
    examAssessment: courseChannelIds(6),
    examResults: courseChannelIds(7),
    examParticipation: courseChannelIds(8),
    examTestRun: courseChannelIds(9),
    textAssessment: courseChannelIds(10),
    programmingParticipation: courseChannelIds(11),
    quizParticipation: courseChannelIds(12),
    import: courseChannelIds(13),
    programmingManagement: courseChannelIds(14),
    general: courseChannelIds(15),
    quizAssessment: courseChannelIds(16),
    exerciseManagement: courseChannelIds(25),
    exerciseParticipation: courseChannelIds(18),
    exerciseAssessment: courseChannelIds(19),
    atlas1: courseChannelIds(20),
    atlas2: courseChannelIds(21),
    lectureManagement: courseChannelIds(22),
    examManagement: courseChannelIds(23),
    testExam: courseChannelIds(24),
} as const;
