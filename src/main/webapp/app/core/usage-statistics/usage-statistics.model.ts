/** Aggregated, anonymous usage numbers of one semester. */
export interface UsageSemester {
    /** Human readable semester such as "WS 2025/26". */
    label: string;
    courses: number;
    students: number;
    activeStudents: number;
    submissions: number;
    lectures: number;
    /** Number of exercises keyed by exercise type, every type is present. */
    exercisesByType: Record<string, number>;
}

export interface PublicUsageStatistics {
    generatedAt: string;
    /** Newest semester first. */
    semesters: UsageSemester[];
}
