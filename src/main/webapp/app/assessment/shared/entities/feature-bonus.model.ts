/** Points a tutor awarded a participation for a feature implemented beyond the task. */
export interface FeatureBonusAward {
    id: number;
    title: string;
    justification?: string;
    points: number;
    createdBy?: string;
    createdDate?: string;
}

/** The values of an award a tutor creates or changes. */
export interface FeatureBonusAwardRequest {
    title: string;
    justification?: string;
    points: number;
}

/** The feature bonus of one participation of an exercise. */
export interface ParticipationFeatureBonus {
    participationId: number;
    points: number;
    /** The points converted with the course settings and capped at their maximum; unset while the course has no conversion. */
    bonusPercent?: number;
}

/** The feature bonus of one student over all exercises and teams of a course. */
export interface StudentFeatureBonus {
    studentId: number;
    points: number;
    /** The points converted with the course settings and capped at their maximum; unset while the course has no conversion. */
    bonusPercent?: number;
}
