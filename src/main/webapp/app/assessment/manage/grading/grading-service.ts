import { Service, inject } from '@angular/core';
import { GradingScale } from 'app/assessment/shared/entities/grading-scale.model';
import { Observable } from 'rxjs';
import { GradeStep, GradeStepsDTO } from 'app/assessment/shared/entities/grade-step.model';
import { map } from 'rxjs/operators';
import { captureException } from 'app/core/error-reporting/capture-exception';
import { Course } from 'app/course/shared/entities/course.model';
import { deepClone } from 'app/foundation/util/deep-clone.util';
import { GradeStepApi } from 'app/openapi/api/grade-step-api';
import { GradingScaleApi } from 'app/openapi/api/grading-scale-api';
import { toGradeStepsDTO } from 'app/assessment/shared/util/grading-scale-response.util';

/**
 * Grade step calculations on the client, and the grade steps of a course or an exam read through the generated grading scale APIs.
 */
@Service()
export class GradingService {
    private gradeStepApi = inject(GradeStepApi);
    private gradingScaleApi = inject(GradingScaleApi);

    /**
     * Finds all grade steps for a course or an exam
     *
     * @param courseId the course for which the grade steps are queried
     * @param examId if present the grade steps for this exam are queried instead
     * @param purpose 'management' reads the exam grade steps from the full instructor grading scale
     */
    findGradeSteps(courseId: number, examId?: number, purpose: 'student' | 'management' = 'student'): Observable<GradeStepsDTO | undefined> {
        if (examId !== undefined && purpose === 'management') {
            return this.gradingScaleApi.getGradingScaleForExam(courseId, examId).pipe(map((gradingScale) => gradingScale?.gradeSteps && toGradeStepsDTO(gradingScale.gradeSteps)));
        }
        const gradeSteps = examId != undefined ? this.gradeStepApi.getAllGradeStepsForExam(courseId, examId) : this.gradeStepApi.getAllGradeStepsForCourse(courseId);
        return gradeSteps.pipe(map((response) => (response ? toGradeStepsDTO(response) : undefined)));
    }

    /**
     * Sorts grade steps by lower bound percentage
     *
     * @param gradeSteps the grade steps to be sorted
     */
    sortGradeSteps(gradeSteps: GradeStep[]): GradeStep[] {
        return gradeSteps.sort((gradeStep1, gradeStep2) => {
            return gradeStep1.lowerBoundPercentage - gradeStep2.lowerBoundPercentage;
        });
    }

    /**
     * Determines whether a given percentage matches the corresponding grade step
     *
     * @param gradeStep the grade step
     * @param percentage the percentage to be matched
     */
    matchGradePercentage(gradeStep: GradeStep, percentage: number): boolean {
        const EPSILON = 0.01;
        if (Math.abs(percentage - gradeStep.lowerBoundPercentage) < EPSILON) {
            return gradeStep.lowerBoundInclusive;
        } else if (Math.abs(percentage - gradeStep.upperBoundPercentage) < EPSILON) {
            return gradeStep.upperBoundInclusive;
        } else {
            return percentage > gradeStep.lowerBoundPercentage && percentage < gradeStep.upperBoundPercentage;
        }
    }

    /**
     * Finds a matching grade step inside a grade step set for the given percentage
     * or returns the highest step if the score is above all steps.
     * Returns undefined if the score is below all grade steps
     *
     * @param gradeSteps the grade step set
     * @param percentage the percentage to be matched
     */
    findMatchingGradeStep(gradeSteps: GradeStep[], percentage: number) {
        const matchingGradeStep = gradeSteps.find((gradeStep) => {
            return this.matchGradePercentage(gradeStep, percentage);
        });

        if (matchingGradeStep) {
            return matchingGradeStep;
        }

        let sortedGradeSteps: GradeStep[] = [];
        gradeSteps.forEach((gradeStep) => sortedGradeSteps.push(deepClone(gradeStep)));
        sortedGradeSteps = this.sortGradeSteps(sortedGradeSteps);

        return percentage > sortedGradeSteps.last()!.upperBoundPercentage ? sortedGradeSteps.last()! : undefined;
    }

    /**
     * Finds a matching grade step among the given grade steps by calculating percentage
     * from the given points and max points.
     * @see findMatchingGradeStep
     * @param gradeSteps
     * @param points
     * @param maxPoints
     */
    findMatchingGradeStepByPoints(gradeSteps: GradeStep[], points: number, maxPoints: number) {
        const percentage = (points / maxPoints) * 100;
        return this.findMatchingGradeStep(gradeSteps, percentage);
    }

    /**
     * Returns the max grade from a given grade step set
     *
     * @param gradeSteps the grade step set
     */
    maxGrade(gradeSteps: GradeStep[]): string {
        const maxGradeStep = gradeSteps.find((gradeStep) => {
            return gradeStep.upperBoundInclusive && gradeStep.upperBoundPercentage === 100;
        });
        return maxGradeStep?.gradeName || '';
    }

    /**
     * Sets the grade points
     *
     * @param gradeSteps the grade steps for which the grade points are set
     * @param maxPoints the max points, based on which the grade points are set
     */
    setGradePoints(gradeSteps: GradeStep[], maxPoints: number) {
        for (const gradeStep of gradeSteps) {
            gradeStep.lowerBoundPoints = (maxPoints * gradeStep.lowerBoundPercentage) / 100;
            gradeStep.upperBoundPoints = (maxPoints * gradeStep.upperBoundPercentage) / 100;
        }
    }

    /**
     * Determines whether all grade steps have their lower and upper bounds set in absolute points
     */
    hasPointsSet(gradeSteps: GradeStep[]): boolean {
        for (const gradeStep of gradeSteps) {
            if (gradeStep.lowerBoundPoints == undefined || gradeStep.upperBoundPoints == undefined || gradeStep.upperBoundPoints === 0) {
                return false;
            }
        }
        return gradeSteps.length !== 0;
    }

    /**
     * Gets the course of the given grading scale either via the exam or directly.
     *
     * @param gradingScale a grading scale belonging to a course or an exam
     */
    getGradingScaleCourse(gradingScale?: GradingScale): Course {
        return (gradingScale?.exam?.course ?? gradingScale?.course)!;
    }

    /**
     * Gets the title of the course or exam related to the given grading scale.
     *
     * @param gradingScale a grading scale belonging to a course or an exam
     */
    getGradingScaleTitle(gradingScale: GradingScale): string | undefined {
        return gradingScale?.exam?.title ?? gradingScale?.course?.title;
    }

    /**
     * Gets the max points of the given grading scale from the related course or exam.
     *
     * @param gradingScale a grading scale belonging to a course or an exam
     */
    getGradingScaleMaxPoints(gradingScale?: GradingScale): number {
        return (gradingScale?.exam?.examMaxPoints ?? gradingScale?.course?.maxPoints) || 0;
    }

    /**
     * Parses the {@link gradeName} as a number to use it in grade and bonus calculations.
     * Accepts both "," and "." as decimal separators.
     *
     * Returns undefined on error.
     *
     * @param gradeName grade name of a grade step
     * @return number value corresponding to the {@link gradeName} or undefined if it is not parseable.
     */
    getNumericValueForGradeName(gradeName: string | undefined): number | undefined {
        if (gradeName == undefined) {
            return undefined;
        }
        gradeName = gradeName.replace(',', '.');
        const numericValue = parseFloat(gradeName);
        if (isNaN(numericValue)) {
            captureException(new Error(`Grade name: ${gradeName} cannot be parsed as float`));
            return undefined;
        }
        return numericValue;
    }
}
