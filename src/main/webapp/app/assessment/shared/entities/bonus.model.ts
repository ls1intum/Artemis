import { BaseEntity } from 'app/foundation/model/base-entity';
import { GradingScale } from 'app/assessment/shared/entities/grading-scale.model';
import { PlagiarismVerdict } from 'app/plagiarism/shared/entities/PlagiarismVerdict';

/** Instantiated and/or deserialized from server data; fields are populated after construction, hence the definite-assignment (!) markers. */
export class Bonus implements BaseEntity {
    public id?: number;
    public bonusStrategy?: BonusStrategy;
    public weight?: number;
    public sourceGradingScale?: GradingScale;
    public bonusToGradingScale?: GradingScale;
}

export enum BonusStrategy {
    GRADES_CONTINUOUS = 'GRADES_CONTINUOUS',
    GRADES_DISCRETE = 'GRADES_DISCRETE',
    POINTS = 'POINTS',
}

/** Instantiated and/or deserialized from server data; fields are populated after construction, hence the definite-assignment (!) markers. */
export class BonusExample {
    public examGrade?: number | string;
    public bonusGrade?: number;
    public finalPoints?: number;
    public finalGrade?: number | string;
    public exceedsMax = false;

    constructor(
        public studentPointsOfBonusTo: number,
        public studentPointsOfBonusSource: number | undefined,
    ) {}
}

export interface BonusResult {
    bonusStrategy?: BonusStrategy;
    bonusFromTitle?: string;
    studentPointsOfBonusSource?: number | undefined;
    bonusGrade?: number | string;
    finalPoints?: number;
    finalGrade?: number | string;
    mostSeverePlagiarismVerdict?: PlagiarismVerdict;
    achievedPresentationScore?: number;
    presentationScoreThreshold?: number;
}

export interface BonusDTO {
    id: number;
    sourceGradingScaleId: number;
    weight?: number;
}

/**
 * Converts a {@link Bonus} entity into a {@link BonusDTO}.
 *
 * @param bonus the bonus entity to convert
 * @returns the corresponding DTO
 */
export const toBonusDTO = (bonus: Bonus): BonusDTO => {
    if (!bonus.id) {
        throw new Error('Bonus id must be defined');
    }
    if (!bonus.sourceGradingScale?.id) {
        throw new Error('Bonus sourceGradingScale id must be defined');
    }

    return {
        id: bonus.id,
        sourceGradingScaleId: bonus.sourceGradingScale.id,
        weight: bonus.weight,
    };
};

/**
 * Converts an array of {@link Bonus} entities into {@link BonusDTO}s.
 *
 * @param bonuses the bonus entities
 * @returns the corresponding DTOs
 */
export const toBonusDTOs = (bonuses: Bonus[] = []): BonusDTO[] => {
    return bonuses.map(toBonusDTO);
};
