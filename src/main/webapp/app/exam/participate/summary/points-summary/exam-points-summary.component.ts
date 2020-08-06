import { Component, Input } from '@angular/core';
import * as moment from 'moment';
import { Exercise } from 'app/entities/exercise.model';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { Exam } from 'app/entities/exam.model';
import { round } from 'app/shared/util/utils';

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
    show(): boolean {
        return !!(this.exam && this.exam.publishResultsDate && moment(this.exam.publishResultsDate).isBefore(this.serverDateService.now()) && this.hasAtLeastOneResult());
    }

    /**
     * Calculate the achieved points of an exercise.
     * @param exercise
     */
    calculateAchievedPoints(exercise: Exercise): number {
        if (ExamPointsSummaryComponent.hasResultScore(exercise)) {
            return round(exercise.maxScore * (exercise.studentParticipations[0].results[0].score / 100), 1);
        }
        return 0;
    }

    /**
     * Calculate the sum of points the student achieved.
     */
    calculatePointsSum(): number {
        if (this.exercises) {
            return round(
                this.exercises.reduce((sum: number, nextExercise: Exercise) => sum + this.calculateAchievedPoints(nextExercise), 0),
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
            return round(
                this.exercises.reduce((sum: number, nextExercise: Exercise) => sum + ExamPointsSummaryComponent.getMaxScore(nextExercise), 0),
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
            exercise.maxScore &&
            exercise.studentParticipations &&
            exercise.studentParticipations.length > 0 &&
            exercise.studentParticipations[0].results &&
            exercise.studentParticipations[0].results.length > 0 &&
            exercise.studentParticipations[0].results[0].score
        );
    }

    private static getMaxScore(exercise: Exercise): number {
        if (exercise && exercise.maxScore) {
            return exercise.maxScore;
        }
        return 0;
    }
}
