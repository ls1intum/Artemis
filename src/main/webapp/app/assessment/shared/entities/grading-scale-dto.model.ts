import { BonusDTO } from 'app/assessment/shared/entities/bonus.model';
import { GradeStepsDTO } from 'app/assessment/shared/entities/grade-step.model';

/**
 * DTO for grading scale response.
 */
export class GradingScaleDTO {
    public id: number;
    public gradeSteps: GradeStepsDTO;
    public bonusStrategy?: string;
    public bonusFrom?: BonusDTO[];
}
