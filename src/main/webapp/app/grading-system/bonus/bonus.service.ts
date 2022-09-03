import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { GradeStep, GradeStepsDTO } from 'app/entities/grade-step.model';
import { Bonus, BonusExample, BonusStrategy } from 'app/entities/bonus.model';
import { GradingScale } from 'app/entities/grading-scale.model';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';

export type EntityResponseType = HttpResponse<Bonus>;

@Injectable({ providedIn: 'root' })
export class BonusService {
    public resourceUrl = SERVER_API_URL + 'api';

    constructor(private http: HttpClient, private gradingSystemService: GradingSystemService) {}

    /**
     * Deletes the bonus
     *
     * @param bonusId the id of the bonus which will be deleted
     */
    deleteBonus(bonusId: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/bonus/${bonusId}`, { observe: 'response' });
    }

    /**
     * Store a new bonus for exam on the server
     *
     * @param courseId the course to which the exam belongs
     * @param examId the exam for which the bonus source will be created
     * @param bonus the bonus source to be created
     */
    createBonusForExam(courseId: number, examId: number, bonus: Bonus): Observable<EntityResponseType> {
        return this.http.post<Bonus>(`${this.resourceUrl}/courses/${courseId}/exams/${examId}/bonus`, this.filterBonusForRequest(bonus), { observe: 'response' });
    }

    /**
     * Update a bonus on the server
     *
     * @param bonus the bonus source to be updated
     */
    updateBonus(bonus: Bonus): Observable<EntityResponseType> {
        return this.http.put<Bonus>(`${this.resourceUrl}/bonus`, this.filterBonusForRequest(bonus), { observe: 'response' });
    }

    /**
     * Retrieves the bonus for exam
     *
     * @param courseId the course to which the exam belongs
     * @param examId the exam for which the bonus source will be retrieved
     */
    findBonusForExam(courseId: number, examId: number): Observable<EntityResponseType> {
        return this.http.get<Bonus>(`${this.resourceUrl}/courses/${courseId}/exams/${examId}/bonus`, { observe: 'response' });
    }

    /**
     * Generates calculation examples to give users an idea how the bonus will contribute to the final grade.
     * The generated values includes max, min grades and some intermediate grade step boundaries.
     *
     * @param bonus bonus.sourceGradingScale.gradeSteps are assumed to be sorted
     * @param bonusTo gradeSteps are assumed to be sorted
     */
    generateBonusExamples(bonus: Bonus, bonusTo: GradeStepsDTO): BonusExample[] {
        if (!bonus.sourceGradingScale) {
            throw new Error(`Bonus.sourceGradingScale is empty: ${bonus.sourceGradingScale}`);
        }
        const bonusExamples = this.generateExampleExamAndBonusPoints(bonusTo, bonus.sourceGradingScale);
        bonusExamples.forEach((bonusExample) => this.calculateFinalGrade(bonusExample, bonus, bonusTo));
        return bonusExamples;
    }

    /**
     * Creates a filtered bonus to send in the request body to reduce payload size and make tracking the changes easier for
     * diagnosis purposes by filtering out irrelevant parts.
     *
     * @param bonus to be sent to the server
     * @private
     */
    private filterBonusForRequest(bonus: Bonus) {
        return {
            ...bonus,
            sourceGradingScale: bonus.sourceGradingScale ? { id: bonus.sourceGradingScale.id } : undefined,
            bonusToGradingScale: undefined,
        };
    }

    /**
     * Determines the example input values for BonusExamples (studentPointsOfBonusTo and studentPointsOfBonusSource).
     * The generated values includes points corresponding to max, min grades and some intermediate grade step boundaries.
     *
     * @param bonusTo gradeSteps are assumed to be sorted
     * @param source gradeSteps are assumed to be sorted
     */
    private generateExampleExamAndBonusPoints(bonusTo: GradeStepsDTO, source: GradingScale): BonusExample[] {
        const examples: BonusExample[] = [];
        examples.push(new BonusExample(0, undefined));

        let bonusToGradeStepIndex = bonusTo.gradeSteps.findIndex((gs) => gs.isPassingGrade);
        if (bonusToGradeStepIndex < 0) {
            throw Error('No passing grade was found for bonusTo grading scale');
        }

        let sourceGradeStepIndex = source.gradeSteps.length - 1;

        const sourceMaxPoints = this.gradingSystemService.getGradingScaleMaxPoints(source);

        for (let i = 0; i < 3; i++) {
            const bonusToGradeStep = bonusTo.gradeSteps[bonusToGradeStepIndex];
            const studentPointsOfBonusTo = this.getIncludedBoundaryPoints(bonusToGradeStep, bonusTo.maxPoints!) ?? bonusToGradeStep.lowerBoundPoints;

            const sourceGradeStep = source.gradeSteps[sourceGradeStepIndex];
            const studentPointsOfBonusSource = this.getIncludedBoundaryPoints(sourceGradeStep, sourceMaxPoints) ?? sourceGradeStep.lowerBoundPoints;

            examples.push(new BonusExample(studentPointsOfBonusTo!, studentPointsOfBonusSource!));

            // Source grade steps descend and bonusTo grade steps ascend to provide somewhat more balanced examples
            // although this is not a hard rule.

            if (i === 0 && sourceGradeStep.lowerBoundPoints === sourceMaxPoints && !sourceGradeStep.lowerBoundInclusive) {
                // Edge case on first iteration: The condition above causes the sourceMaxPoints to be included in the
                // next source grade step so we should skip it to not have examples with duplicate bonus values.
                sourceGradeStepIndex = this.modulo(sourceGradeStepIndex - 1, source.gradeSteps.length);
            }

            sourceGradeStepIndex = this.modulo(sourceGradeStepIndex - 1, source.gradeSteps.length);
            bonusToGradeStepIndex = this.modulo(bonusToGradeStepIndex + 1, bonusTo.gradeSteps.length);
        }

        bonusToGradeStepIndex = bonusTo.gradeSteps.length - 1;
        const lastBonusToGradeStep = bonusTo.gradeSteps[bonusToGradeStepIndex];
        const lastStudentPointsOfBonusTo = this.getIncludedBoundaryPoints(lastBonusToGradeStep, bonusTo.maxPoints!) ?? lastBonusToGradeStep.lowerBoundPoints;

        let lastSourceGradeStep = source.gradeSteps[sourceGradeStepIndex];
        if (this.gradingSystemService.getNumericValueForGradeName(lastSourceGradeStep.gradeName) === 0) {
            // A non-zero bonus serves better as an example.
            lastSourceGradeStep = source.gradeSteps[source.gradeSteps.length - 1];
        }
        const lastStudentPointsOfBonusSource = this.getIncludedBoundaryPoints(lastSourceGradeStep, sourceMaxPoints) ?? lastSourceGradeStep.lowerBoundPoints;

        examples.push(new BonusExample(lastStudentPointsOfBonusTo!, lastStudentPointsOfBonusSource!));

        return examples;
    }

    /**
     * Applies bonus from bonus.sourceGradingScale to bonusToGradingScale grade steps with student points from bonusExample.
     *
     * @param bonusExample Modified by this method. studentPointsOfBonusSource and studentPointsOfBonusSource fields are read, others are (over)written
     * @param bonus Contains calculation instructions and source grading scale
     * @param bonusTo Grading scale that will have its grades improved by bonus
     */
    calculateFinalGrade(bonusExample: BonusExample, bonus: Bonus, bonusTo: GradeStepsDTO) {
        const examGradeStep = this.gradingSystemService.findMatchingGradeStepByPoints(bonusTo.gradeSteps, bonusExample.studentPointsOfBonusTo, bonusTo.maxPoints!);
        bonusExample.examGrade = examGradeStep?.gradeName;

        if (!examGradeStep?.isPassingGrade || !bonus.sourceGradingScale) {
            bonusExample.bonusGrade = 0;
            bonusExample.finalPoints = bonusExample.studentPointsOfBonusTo;
            bonusExample.finalGrade = bonusExample.examGrade;
            return;
        }

        const bonusGradeStep = this.gradingSystemService.findMatchingGradeStepByPoints(
            bonus.sourceGradingScale.gradeSteps,
            bonusExample.studentPointsOfBonusSource ?? 0,
            this.gradingSystemService.getGradingScaleMaxPoints(bonus.sourceGradingScale),
        );
        bonusExample.bonusGrade = this.gradingSystemService.getNumericValueForGradeName(bonusGradeStep?.gradeName);

        this.calculateBonusForStrategy(bonusExample, bonus, bonusTo);
    }

    /**
     * {@see calculateFinalGrade}. This method contains the calculation logic for each bonus strategy. Does not perform passing grade check.
     * @param bonusExample Modified by this method. studentPointsOfBonusSource, studentPointsOfBonusSource and bonusGrade fields are read, others are (over)written
     * @param bonus Contains calculation instructions and source grading scale
     * @param bonusTo Grading scale that will have its grades improved by bonus
     */
    private calculateBonusForStrategy(bonusExample: BonusExample, bonus: Bonus, bonusTo: GradeStepsDTO) {
        const course = this.gradingSystemService.getGradingScaleCourse(bonus.bonusToGradingScale);
        switch (bonus.bonusStrategy) {
            case BonusStrategy.POINTS: {
                bonusExample.finalPoints = roundValueSpecifiedByCourseSettings(bonusExample.studentPointsOfBonusTo + (bonus.weight ?? 1) * bonusExample.bonusGrade!, course);
                if (this.doesBonusExceedMax(bonusExample.finalPoints, bonusTo.maxPoints!, bonus.weight!)) {
                    bonusExample.exceedsMax = true;
                    bonusExample.finalPoints = bonusTo.maxPoints ?? 0;
                }
                const finalGradeStep = this.gradingSystemService.findMatchingGradeStepByPoints(bonusTo.gradeSteps, bonusExample.finalPoints, bonusTo.maxPoints!);
                bonusExample.finalGrade = finalGradeStep?.gradeName;
                break;
            }
            case BonusStrategy.GRADES_CONTINUOUS: {
                const examGradeNumericValue = this.gradingSystemService.getNumericValueForGradeName(bonusExample.examGrade as string)!;
                bonusExample.finalGrade = roundValueSpecifiedByCourseSettings(examGradeNumericValue + (bonus.weight ?? 1) * bonusExample.bonusGrade!, course);
                const maxGrade = this.gradingSystemService.maxGrade(bonusTo.gradeSteps);
                const maxGradeNumericValue = this.gradingSystemService.getNumericValueForGradeName(maxGrade)!;
                if (this.doesBonusExceedMax(bonusExample.finalGrade, maxGradeNumericValue, bonus.weight!)) {
                    bonusExample.exceedsMax = true;
                    bonusExample.finalGrade = maxGrade;
                }
                break;
            }
            case BonusStrategy.GRADES_DISCRETE: {
                throw new Error('GRADES_DISCRETE bonus strategy not yet implemented');
            }
        }
    }

    /**
     * Returns true if valueWithBonus exceeds the maxValue in the direction given by calculationSign.
     * @param valueWithBonus achieved points or numeric grade with bonus applied
     * @param maxValue max points or max grade (numeric)
     * @param calculationSign a negative or positive number to indicate decreasing or increasing direction, respectively
     */
    doesBonusExceedMax(valueWithBonus: number, maxValue: number, calculationSign: number) {
        return (valueWithBonus - maxValue) * calculationSign! > 0;
    }

    /**
     * Get the included points
     * @param gradeStep
     * @param maxPoints
     */
    getIncludedBoundaryPoints(gradeStep: GradeStep, maxPoints: number) {
        if (gradeStep.lowerBoundInclusive) {
            return gradeStep.lowerBoundPoints;
        }
        if (gradeStep.upperBoundInclusive) {
            return Math.min(gradeStep.upperBoundPoints!, maxPoints);
        }
        return undefined;
    }

    /**
     * As opposed to % operator, this method always returns a non-negative number.
     * @param n as in n mod m
     * @param m as in n mod m
     * @private
     */
    private modulo(n: number, m: number) {
        return ((n % m) + m) % m;
    }
}
