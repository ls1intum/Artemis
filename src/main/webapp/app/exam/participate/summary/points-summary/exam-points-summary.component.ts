import { ChangeDetectorRef, Component, Input, OnInit } from '@angular/core';
import * as moment from 'moment';
import { Exercise, IncludedInOverallScore } from 'app/entities/exercise.model';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { Exam } from 'app/entities/exam.model';
import { round } from 'app/shared/util/utils';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { catchError } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { GradeType } from 'app/entities/grading-scale.model';

@Component({
    selector: 'jhi-exam-points-summary',
    styleUrls: ['./exam-points-summary.component.scss'],
    templateUrl: './exam-points-summary.component.html',
})
export class ExamPointsSummaryComponent implements OnInit {
    readonly IncludedInOverallScore = IncludedInOverallScore;
    @Input() exercises: Exercise[];
    @Input() exam: Exam;
    @Input() courseId: number;

    gradingScaleExists = false;
    isBonus = false;
    hasPassed = false;
    grade?: string;

    constructor(
        private serverDateService: ArtemisServerDateService,
        public exerciseService: ExerciseService,
        private changeDetector: ChangeDetectorRef,
        private gradingSystemService: GradingSystemService,
    ) {}

    ngOnInit() {
        if (this.exam && this.exam.publishResultsDate && moment(this.exam.publishResultsDate).isBefore(this.serverDateService.now())) {
            this.calculateExamGrade();
        }
    }

    /**
     * The points summary table will only be shown if:
     * - exam.publishResultsDate is set
     * - we are after the exam.publishResultsDate
     * - at least one exercise has a result
     */
    show(): boolean {
        return !!(this.exam && this.exam.publishResultsDate && moment(this.exam.publishResultsDate).isBefore(this.serverDateService.now()) && this.hasAtLeastOneResult());
    }

    /**
     * Calculate the student's exam grade if a grading scale exists for the exam
     */
    calculateExamGrade() {
        const achievedPointsRelative = (this.calculatePointsSum() / this.calculateMaxPointsSum()) * 100;
        this.gradingSystemService
            .matchPercentageToGradeStepForExam(this.courseId, this.exam!.id!, achievedPointsRelative)
            .pipe(
                catchError((error: HttpErrorResponse) => {
                    if (error.status === 404) {
                        return of(undefined);
                    }
                    return throwError(error);
                }),
            )
            .subscribe((gradeObservable) => {
                if (gradeObservable && gradeObservable!.body) {
                    const gradeDTO = gradeObservable!.body;
                    this.gradingScaleExists = true;
                    this.grade = gradeDTO.gradeName;
                    this.hasPassed = gradeDTO.isPassingGrade;
                    this.isBonus = gradeDTO.gradeType === GradeType.BONUS;
                    this.changeDetector.detectChanges();
                }
            });
    }

    /**
     * Calculate the achieved points of an exercise.
     * @param exercise
     */
    calculateAchievedPoints(exercise: Exercise): number {
        if (ExamPointsSummaryComponent.hasResultScore(exercise)) {
            return round(exercise.maxPoints! * (exercise.studentParticipations![0].results![0].score! / 100), 1);
        }
        return 0;
    }

    /**
     * Calculate the sum of points the student achieved in exercises that count towards his or her score.
     */
    calculatePointsSum(): number {
        if (this.exercises) {
            const exercisesIncluded = this.exercises?.filter((exercise) => exercise.includedInOverallScore !== IncludedInOverallScore.NOT_INCLUDED);
            return round(
                exercisesIncluded.reduce((sum: number, nextExercise: Exercise) => sum + this.calculateAchievedPoints(nextExercise), 0),
                1,
            );
        }
        return 0;
    }

    /**
     * Calculate the max. achievable points.
     */
    calculateMaxPointsSum(): number {
        if (this.exercises) {
            const exercisesIncluded = this.exercises?.filter((exercise) => exercise.includedInOverallScore === IncludedInOverallScore.INCLUDED_COMPLETELY);
            return round(
                exercisesIncluded.reduce((sum: number, nextExercise: Exercise) => sum + ExamPointsSummaryComponent.getMaxScore(nextExercise), 0),
                1,
            );
        }
        return 0;
    }

    /**
     * Calculate the max. achievable bonusPoints.
     */
    calculateMaxBonusPointsSum(): number {
        if (this.exercises) {
            const exercisesIncluded = this.exercises?.filter((exercise) => exercise.includedInOverallScore !== IncludedInOverallScore.NOT_INCLUDED);
            return round(
                exercisesIncluded.reduce((sum: number, nextExercise: Exercise) => sum + ExamPointsSummaryComponent.getBonusPoints(nextExercise), 0),
                1,
            );
        }
        return 0;
    }

    private hasAtLeastOneResult(): boolean {
        if (this.exercises && this.exercises.length > 0) {
            return this.exercises.some((exercise) => {
                return (
                    exercise.studentParticipations &&
                    exercise.studentParticipations.length > 0 &&
                    exercise.studentParticipations[0].results &&
                    exercise.studentParticipations[0].results.length > 0
                );
            });
        }
        return false;
    }

    private static hasResultScore(exercise: Exercise): boolean {
        return !!(
            exercise &&
            exercise.maxPoints &&
            exercise.studentParticipations &&
            exercise.studentParticipations.length > 0 &&
            exercise.studentParticipations[0].results &&
            exercise.studentParticipations[0].results.length > 0 &&
            exercise.studentParticipations[0].results[0].score
        );
    }

    private static getMaxScore(exercise: Exercise): number {
        if (exercise && exercise.maxPoints) {
            return exercise.maxPoints;
        }
        return 0;
    }

    private static getBonusPoints(exercise: Exercise): number {
        if (exercise && exercise.includedInOverallScore === IncludedInOverallScore.INCLUDED_AS_BONUS && exercise.maxPoints) {
            return exercise.maxPoints;
        } else if (exercise && exercise.bonusPoints) {
            return exercise.bonusPoints;
        } else {
            return 0;
        }
    }
}
