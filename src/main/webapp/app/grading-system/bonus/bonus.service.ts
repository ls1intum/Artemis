import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { GradeStep, GradeStepsDTO } from 'app/entities/grade-step.model';
import { Bonus, BonusExample, BonusStrategy } from 'app/entities/bonus.model';
import { GradingScale } from 'app/entities/grading-scale.model';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { captureException } from '@sentry/angular';

export type EntityResponseType = HttpResponse<Bonus>;

@Injectable({ providedIn: 'root' })
export class BonusService {
    public resourceUrl = SERVER_API_URL + 'api';

    constructor(private http: HttpClient, private gradingSystemService: GradingSystemService) {}

    // /**
    //  * Store a new bonus source for course on the server
    //  *
    //  * @param targetExamId the exam for which the bonus source will be created
    //  * @param bonus the bonus source to be created
    //  */
    // createBonusForCourse(targetExamId: number, bonus: Bonus): Observable<EntityResponseType> {
    //     return this.http.post<Bonus>(`${this.resourceUrl}/exams/${targetExamId}/bonus`, bonus, { observe: 'response' });
    // }

    // /**
    //  * Update a bonus source for course on the server
    //  *
    //  * @param targetExamId the course for which the bonus source will be updated
    //  * @param bonus the bonus source to be updated
    //  */
    // updateBonusForCourse(targetExamId: number, bonus: Bonus): Observable<EntityResponseType> {
    //     return this.http.put<Bonus>(`${this.resourceUrl}/${targetExamId}/bonus`, bonus, { observe: 'response' });
    // }
    //
    // /**
    //  * Retrieves the bonus source for course
    //  *
    //  * @param targetExamId the course for which the bonus source will be retrieved
    //  */
    // findBonusForCourse(targetExamId: number): Observable<EntityResponseType> {
    //     return this.http.get<Bonus>(`${this.resourceUrl}/${targetExamId}/bonus`, { observe: 'response' });
    // }

    /**
     * Deletes the bonus source for course
     *
     * @param bonusId the id of the bonus which will be deleted
     */
    deleteBonus(bonusId: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/bonus/${bonusId}`, { observe: 'response' });
    }

    /**
     * Store a new bonus source for exam on the server
     *
     * @param courseId the course to which the exam belongs
     * @param examId the exam for which the bonus source will be created
     * @param bonus the bonus source to be created
     */
    createBonusForExam(courseId: number, examId: number, bonus: Bonus): Observable<EntityResponseType> {
        return this.http.post<Bonus>(`${this.resourceUrl}/courses/${courseId}/exams/${examId}/bonus`, bonus, { observe: 'response' });
    }

    /**
     * Update a bonus on the server
     *
     * @param bonus the bonus source to be updated
     */
    updateBonus(bonus: Bonus): Observable<EntityResponseType> {
        return this.http.put<Bonus>(`${this.resourceUrl}/bonus`, bonus, { observe: 'response' });
    }

    /**
     * Retrieves the bonus source for exam
     *
     * @param courseId the course to which the exam belongs
     * @param examId the exam for which the bonus source will be retrieved
     */
    findBonusWithTargetExam(courseId: number, examId: number): Observable<EntityResponseType> {
        return this.http.get<Bonus>(`${this.resourceUrl}/courses/${courseId}/exams/${examId}/bonus`, { observe: 'response' });
    }

    /**
     * Deletes the bonus source for exam
     *
     * @param targetExamId the course to which the exam belongs
     * @param examId the exam for which the bonus source will be deleted
     */
    deleteBonusForExam(targetExamId: number, examId: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/courses/${targetExamId}/exams/${examId}/bonus`, { observe: 'response' });
    }

    // /**
    //  * Finds all grade steps for a course or an exam
    //  *
    //  * @param targetExamId the course for which the grade steps are queried
    //  * @param examId if present the grade steps for this exam are queried instead
    //  */
    // findGradeSteps(targetExamId: number, examId?: number): Observable<GradeStepsDTO | undefined> {
    //     let gradeStepsObservable: Observable<HttpResponse<GradeStepsDTO>>;
    //     if (examId != undefined) {
    //         gradeStepsObservable = this.findGradeStepsForExam(targetExamId, examId);
    //     } else {
    //         gradeStepsObservable = this.findGradeStepsForCourse(targetExamId);
    //     }
    //     return gradeStepsObservable.pipe(
    //         map((gradeStepsDTO) => {
    //             if (gradeStepsDTO && gradeStepsDTO.body) {
    //                 return gradeStepsDTO.body;
    //             }
    //         }),
    //     );
    // }

    /**
     * TODO: Ata
     * @param bonus bonus.source.gradeSteps are assumed to be sorted
     * @param target gradeSteps are assumed to be sorted
     */
    generateBonusExamples(bonus: Bonus, target: GradeStepsDTO): BonusExample[] {
        if (!bonus.source) {
            throw new Error(`Bonus.source is empty: ${bonus.source}`);
        }
        const bonusExamples = this.generateExampleExamAndBonusPoints(target, bonus.source);
        bonusExamples.forEach((bonusExample) => this.calculateFinalGrade(bonusExample, bonus, target, bonus.source!));
        return bonusExamples;
    }

    /**
     * TODO: Ata
     * @param target gradeSteps are assumed to be sorted
     * @param source gradeSteps are assumed to be sorted
     */
    private generateExampleExamAndBonusPoints(target: GradeStepsDTO, source: GradingScale): BonusExample[] {
        const examples: BonusExample[] = [];
        examples.push(new BonusExample(0, undefined));

        let targetGradeStepIndex = target.gradeSteps.findIndex((gs) => gs.isPassingGrade);
        let sourceGradeStepIndex = source.gradeSteps.length - 1;

        const sourceMaxPoints = this.gradingSystemService.getGradingScaleMaxPoints(source);

        for (let i = 0; i < 3; i++) {
            const targetGradeStep = target.gradeSteps[targetGradeStepIndex];
            const examStudentPoints = this.getIncludedBoundaryPoints(targetGradeStep, target.maxPoints!) ?? targetGradeStep.lowerBoundPoints;

            const sourceGradeStep = source.gradeSteps[sourceGradeStepIndex];
            const bonusStudentPoints = this.getIncludedBoundaryPoints(sourceGradeStep, sourceMaxPoints) ?? sourceGradeStep.lowerBoundPoints;

            examples.push(new BonusExample(examStudentPoints!, bonusStudentPoints!));

            sourceGradeStepIndex = this.modulo(sourceGradeStepIndex - 1, source.gradeSteps.length);
            targetGradeStepIndex = this.modulo(targetGradeStepIndex + 1, target.gradeSteps.length);
        }

        targetGradeStepIndex = target.gradeSteps.length - 1;
        const lastTargetGradeStep = target.gradeSteps[targetGradeStepIndex];
        const lastExamStudentPoints = this.getIncludedBoundaryPoints(lastTargetGradeStep, target.maxPoints!) ?? lastTargetGradeStep.lowerBoundPoints;

        sourceGradeStepIndex = source.gradeSteps.length - 1;
        const lastSourceGradeStep = source.gradeSteps[sourceGradeStepIndex];
        const lastBonusStudentPoints = this.getIncludedBoundaryPoints(lastSourceGradeStep, sourceMaxPoints) ?? lastSourceGradeStep.lowerBoundPoints;

        examples.push(new BonusExample(lastExamStudentPoints!, lastBonusStudentPoints!));

        return examples;
    }

    calculateFinalGrade(bonusExample: BonusExample, bonus: Bonus, target: GradeStepsDTO, source: GradingScale) {
        const examGradeStep = this.gradingSystemService.findMatchingGradeStepByPoints(target.gradeSteps, bonusExample.examStudentPoints, target.maxPoints!);
        bonusExample.examGrade = examGradeStep?.gradeName;

        const bonusGradeStep = this.gradingSystemService.findMatchingGradeStepByPoints(
            source.gradeSteps,
            bonusExample.bonusStudentPoints ?? 0,
            this.gradingSystemService.getGradingScaleMaxPoints(source),
        );
        bonusExample.bonusGrade = this.gradingSystemService.getNumericValueForGradeName(bonusGradeStep?.gradeName);

        this.calculateBonusForStrategy(bonusExample, bonus, target);
        // bonusExample.calculatedBonus = undefined; // TODO: Ata
    }

    calculateBonusForStrategy(bonusExample: BonusExample, bonus: Bonus, target: GradeStepsDTO) {
        switch (bonus.bonusStrategy) {
            case BonusStrategy.POINTS:
                bonusExample.finalPoints = bonusExample.examStudentPoints + (bonus.calculationSign ?? 1) * bonusExample.bonusGrade!;
                if (this.doesBonusExceedMax(bonusExample.finalPoints, target.maxPoints!, bonus.calculationSign!)) {
                    bonusExample.finalPoints = target.maxPoints ?? 0;
                }
                const finalGradeStep = this.gradingSystemService.findMatchingGradeStepByPoints(target.gradeSteps, bonusExample.finalPoints, target.maxPoints!);
                bonusExample.finalGrade = finalGradeStep?.gradeName;
                break;
            case BonusStrategy.GRADES_CONTINUOUS:
                const examGradeNumericValue = this.gradingSystemService.getNumericValueForGradeName(bonusExample.examGrade as string)!;
                bonusExample.finalGrade = examGradeNumericValue + (bonus.calculationSign ?? 1) * bonusExample.bonusGrade!;
                const maxGrade = this.gradingSystemService.maxGrade(target.gradeSteps);
                const maxGradeNumericValue = this.gradingSystemService.getNumericValueForGradeName(maxGrade)!;
                if (this.doesBonusExceedMax(bonusExample.finalGrade, maxGradeNumericValue, bonus.calculationSign!)) {
                    bonusExample.finalGrade = maxGrade;
                }
                break;
            case BonusStrategy.GRADES_DISCRETE:
                // TODO: Ata
                break;
        }
    }

    private doesBonusExceedMax(valueWithBonus: number, maxValue: number, calculationSign: number) {
        return (valueWithBonus - maxValue) * calculationSign! > 0;
    }

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
