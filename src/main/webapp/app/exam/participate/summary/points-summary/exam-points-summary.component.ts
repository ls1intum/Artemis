import { Component, Input } from '@angular/core';
import { Exercise } from 'app/entities/exercise.model';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { Exam } from 'app/entities/exam.model';

@Component({
    selector: 'jhi-exam-points-summary',
    templateUrl: './exam-points-summary.component.html',
})
export class ExamPointsSummaryComponent {
    @Input() exercises: Exercise[];
    @Input() exam: Exam;

    constructor(private serverDateService: ArtemisServerDateService) {}

    /**
     * The points summary table will only be shown if:
     * - exam.publishResultsDate is set
     * - we are after the exam.publishResultsDate
     * - at least one exercise has a result
     */
    show() {
        return !!(this.exam && this.exam.publishResultsDate && this.exam.publishResultsDate.isBefore(this.serverDateService.now()) && this.hasAtLeastOneResult());
    }

    /**
     * Calculate the achieved points of an exercise.
     * @param exercise
     */
    calculateAchievedPointsForExercise(exercise: Exercise): number | '-' {
        if (ExamPointsSummaryComponent.hasResultScore(exercise)) {
            return ExamPointsSummaryComponent.getResultPoints(exercise);
        }
        return '-';
    }

    /**
     * Calculate the sum of points the student achieved.
     */
    calculatePointsSum(): number {
        if (this.exercises) {
            return this.exercises.reduce((sum: number, nextExercise: Exercise) => sum + ExamPointsSummaryComponent.getResultPoints(nextExercise), 0);
        }
        return 0;
    }

    /**
     * Calculate the max. achievable points.
     */
    calculateMaxPointsSum(): number {
        if (this.exercises) {
            return this.exercises.reduce((sum: number, nextExercise: Exercise) => sum + ExamPointsSummaryComponent.getMaxScore(nextExercise), 0);
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
            exercise.maxScore &&
            exercise.studentParticipations &&
            exercise.studentParticipations.length > 0 &&
            exercise.studentParticipations[0].results &&
            exercise.studentParticipations[0].results.length > 0 &&
            exercise.studentParticipations[0].results[0].score
        );
    }

    private static getResultPoints(exercise: Exercise): number {
        if (ExamPointsSummaryComponent.hasResultScore(exercise)) {
            return exercise.maxScore * (exercise.studentParticipations[0].results[0].score / 100);
        }
        return 0;
    }

    private static getMaxScore(exercise: Exercise): number {
        if (exercise && exercise.maxScore) {
            return exercise.maxScore;
        }
        return 0;
    }
}
