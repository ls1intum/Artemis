/** Aggregated, anonymous build numbers of one semester. */
export interface BuildUsage {
    /** Number of finished build jobs: successful, failed, errored or timed out. */
    count: number;
    /** Average time from start to completion of the successful build jobs, absent without such jobs. */
    averageDurationSeconds?: number;
}

/** Aggregated, anonymous communication numbers of one semester. They contain no content and no authors. */
export interface CommunicationUsage {
    posts: number;
    replies: number;
    reactions: number;
    /** Published FAQs. */
    faqs: number;
}

/** Aggregated, anonymous usage numbers of one semester. */
export interface UsageSemester {
    /** Human readable semester such as "WS 2025/26". */
    label: string;
    courses: number;
    students: number;
    /** Student enrolments, not unique: a student of several courses is counted once per course. Absent in responses of a server that predates it. */
    enrollments?: number;
    /** Unique people with the instructor role in these courses. Absent in responses of a server that predates it. */
    instructors?: number;
    /** Unique people with the tutor or editor role in these courses. Absent in responses of a server that predates it. */
    tutors?: number;
    activeStudents: number;
    submissions: number;
    lectures: number;
    /** Units of these lectures. Absent in responses of a server that predates it. */
    lectureUnits?: number;
    /** Number of exercises keyed by exercise type, every type is present. */
    exercisesByType: Record<string, number>;
    /** Absent for a semester that started before build jobs are deleted, and in responses of a server that predates the builds. */
    builds?: BuildUsage;
    /** Absent in responses of a server that predates the communication numbers. */
    communication?: CommunicationUsage;
    /** Messages between users and Iris, absent if Iris is not enabled on this instance. */
    irisMessages?: number;
    /** Tutorial groups, absent if tutorial groups are not enabled on this instance. */
    tutorialGroups?: number;
    /** Real exams (no test exams, no titles starting with test or mock), absent if exams are not enabled on this instance. */
    exams?: number;
    /** Registrations of students for these exams, not unique: a student registered for several exams is counted once per exam. Absent if exams are not enabled. */
    examRegistrations?: number;
    /** Started exams of students in these exams, not unique: a student who took several exams is counted once per exam. Absent if exams are not enabled. */
    examParticipations?: number;
    /** Competencies without prerequisites, absent if the atlas module is not enabled on this instance. */
    competencies?: number;
}

export interface PublicUsageStatistics {
    generatedAt: string;
    /** Newest semester first. */
    semesters: UsageSemester[];
}
