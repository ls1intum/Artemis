import { Bonus, BonusStrategy } from 'app/assessment/shared/entities/bonus.model';
import { GradingScale } from 'app/assessment/shared/entities/grading-scale.model';
import { Course } from 'app/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { hydrate } from 'app/foundation/util/deep-clone.util';
import { gradeTypeByResponse, toGradeStep } from 'app/assessment/shared/util/grading-scale-response.util';
import type { BonusRequest } from 'app/openapi/model/bonus-request';
import type { BonusResponse } from 'app/openapi/model/bonus-response';
import type { CourseForBonus } from 'app/openapi/model/course-for-bonus';
import type { GradingScaleForBonus } from 'app/openapi/model/grading-scale-for-bonus';

const bonusStrategyByResponse: Record<NonNullable<BonusResponse['bonusStrategy']>, BonusStrategy> = {
    GRADES_CONTINUOUS: BonusStrategy.GRADES_CONTINUOUS,
    GRADES_DISCRETE: BonusStrategy.GRADES_DISCRETE,
    POINTS: BonusStrategy.POINTS,
};

function toCourse(course: CourseForBonus): Course {
    return { id: course.id, title: course.title, maxPoints: course.maxPoints, accuracyOfScores: course.accuracyOfScores };
}

function toGradingScale(scale: GradingScaleForBonus): GradingScale {
    return hydrate(new GradingScale(), {
        id: scale.id,
        gradeType: scale.gradeType ? gradeTypeByResponse[scale.gradeType] : undefined,
        bonusStrategy: scale.bonusStrategy ? bonusStrategyByResponse[scale.bonusStrategy] : undefined,
        plagiarismGrade: scale.plagiarismGrade,
        noParticipationGrade: scale.noParticipationGrade,
        presentationsNumber: scale.presentationsNumber,
        presentationsWeight: scale.presentationsWeight,
        gradeSteps: scale.gradeSteps?.map(toGradeStep),
        course: scale.course && toCourse(scale.course),
        exam: scale.exam && toExam(scale.exam),
    });
}

function toExam(exam: NonNullable<GradingScaleForBonus['exam']>): Exam {
    return { id: exam.id, title: exam.title, examMaxPoints: exam.examMaxPoints, course: exam.course && toCourse(exam.course) };
}

/**
 * Builds the bonus the editor works with from the bonus response; absent grade steps, course and exam stay absent.
 */
export function toBonus(response: BonusResponse): Bonus {
    return {
        id: response.id,
        weight: response.weight,
        bonusStrategy: response.bonusStrategy ? bonusStrategyByResponse[response.bonusStrategy] : undefined,
        sourceGradingScale: response.sourceGradingScale && toGradingScale(response.sourceGradingScale),
        bonusToGradingScale: response.bonusToGradingScale && toGradingScale(response.bonusToGradingScale),
    };
}

/**
 * Picks what the server reads from the bonus: the id, weight and strategy, and only the id of the source grading scale.
 */
export function toBonusRequest(bonus: Bonus): BonusRequest {
    return {
        id: bonus.id,
        weight: bonus.weight,
        bonusStrategy: bonus.bonusStrategy,
        sourceGradingScale: bonus.sourceGradingScale ? { id: bonus.sourceGradingScale.id } : undefined,
    };
}
