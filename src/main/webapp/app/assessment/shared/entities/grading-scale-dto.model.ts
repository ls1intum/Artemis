import { GradeStepDTO } from 'app/assessment/shared/entities/grading-scale-request-dto.model';
import { BonusDTO } from 'app/assessment/shared/entities/bonus.model';

/**
 * DTO for grading scale response.
 */
export class GradingScaleDTO {
    public id: number;
    public gradeSteps: GradeStepDTO[];
    public bonusStrategy?: string;
    public bonusFrom?: Set<BonusDTO>;
}
