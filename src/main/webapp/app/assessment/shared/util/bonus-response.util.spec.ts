import { describe, expect, it } from 'vitest';
import { toBonus, toBonusRequest } from 'app/assessment/shared/util/bonus-response.util';
import { BonusStrategy } from 'app/assessment/shared/entities/bonus.model';
import { GradeType } from 'app/assessment/shared/entities/grading-scale.model';

describe('bonus response util', () => {
    it('maps the bonus response to the editor bonus', () => {
        const bonus = toBonus({
            id: 4,
            weight: -1,
            bonusStrategy: 'GRADES_CONTINUOUS',
            sourceGradingScale: {
                id: 9,
                gradeType: 'BONUS',
                gradeSteps: [{ gradeName: '1.0', lowerBoundPercentage: 0, lowerBoundInclusive: true, upperBoundPercentage: 100, upperBoundInclusive: true, isPassingGrade: true }],
                exam: { id: 2, title: 'Exam', examMaxPoints: 80, course: { id: 1, accuracyOfScores: 2 } },
            },
            bonusToGradingScale: { id: 10, gradeType: 'GRADE' },
        });

        expect(bonus.bonusStrategy).toBe(BonusStrategy.GRADES_CONTINUOUS);
        expect(bonus.sourceGradingScale?.gradeType).toBe(GradeType.BONUS);
        expect(bonus.sourceGradingScale?.gradeSteps).toEqual([
            { gradeName: '1.0', lowerBoundPercentage: 0, lowerBoundInclusive: true, upperBoundPercentage: 100, upperBoundInclusive: true, isPassingGrade: true },
        ]);
        expect(bonus.sourceGradingScale?.exam).toEqual({ id: 2, title: 'Exam', examMaxPoints: 80, course: { id: 1, title: undefined, maxPoints: undefined, accuracyOfScores: 2 } });
        expect(bonus.bonusToGradingScale?.gradeType).toBe(GradeType.GRADE);
    });

    it('keeps an absent source grading scale absent', () => {
        const bonus = toBonus({ id: 4, weight: 1, bonusStrategy: 'POINTS' });

        expect(bonus.sourceGradingScale).toBeUndefined();
        expect(bonus.bonusToGradingScale).toBeUndefined();
    });

    it('sends only the id of the source grading scale', () => {
        const request = toBonusRequest({
            id: 3,
            weight: 1,
            bonusStrategy: BonusStrategy.POINTS,
            sourceGradingScale: { id: 5, gradeType: GradeType.BONUS, gradeSteps: [] },
            bonusToGradingScale: { id: 6, gradeType: GradeType.GRADE, gradeSteps: [] },
        });

        expect(request).toEqual({ id: 3, weight: 1, bonusStrategy: 'POINTS', sourceGradingScale: { id: 5 } });
    });
});
