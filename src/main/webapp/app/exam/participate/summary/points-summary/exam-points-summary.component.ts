import { ChangeDetectorRef, Component, Input, OnInit } from '@angular/core';
import dayjs from 'dayjs/esm';
import { Exercise, IncludedInOverallScore } from 'app/entities/exercise.model';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { GradeType } from 'app/entities/grading-scale.model';
import { faClipboard } from '@fortawesome/free-solid-svg-icons';
import { StudentExamWithGradeDTO } from 'app/exam/exam-scores/exam-score-dtos.model';

@Component({
    selector: 'jhi-exam-points-summary',
    styleUrls: ['./exam-points-summary.component.scss'],
    templateUrl: './exam-points-summary.component.html',
})
export class ExamPointsSummaryComponent implements OnInit {
    readonly IncludedInOverallScore = IncludedInOverallScore;
    @Input() studentExamWithGrade: StudentExamWithGradeDTO;

    gradingScaleExists = false;
    isBonus = false;
    hasPassed = false;
    grade?: string;

    // Icons
    faClipboard = faClipboard;

    constructor(private serverDateService: ArtemisServerDateService, public exerciseService: ExerciseService, private changeDetector: ChangeDetectorRef) {}

    ngOnInit() {
        if (this.isExamResultPublished()) {
            this.setExamGrade();
        }
    }

    /**
     * The points summary table will only be shown if:
     * - exam.publishResultsDate is set
     * - we are after the exam.publishResultsDate
     * - at least one exercise has a result
     */
    show(): boolean {
        return !!(this.isExamResultPublished() && this.hasAtLeastOneResult());
    }

    private isExamResultPublished() {
        const exam = this.studentExamWithGrade?.studentExam?.exam;
        return exam && exam.publishResultsDate && dayjs(exam.publishResultsDate).isBefore(this.serverDateService.now());
    }

    /**
     * Sets the student's exam grade if a grading scale exists for the exam
     */
    setExamGrade() {
        if (this.studentExamWithGrade?.studentResult?.overallGrade != undefined) {
            this.gradingScaleExists = true;
            this.grade = this.studentExamWithGrade.studentResult.overallGrade;
            this.isBonus = this.studentExamWithGrade.gradeType === GradeType.BONUS;
            this.hasPassed = !!this.studentExamWithGrade.studentResult.hasPassed;
            this.changeDetector.detectChanges();
        }
    }

    getAchievedPointsSum() {
        return this.studentExamWithGrade?.studentResult.overallPointsAchieved ?? 0;
    }

    getMaxPointsSum() {
        return this.studentExamWithGrade?.maxPoints ?? 0;
    }

    getAchievedPoints(exercise: Exercise): number {
        return this.studentExamWithGrade?.achievedPointsPerExercise?.[exercise.id!] ?? 0;
    }

    /**
     * Returns the max. achievable bonusPoints.
     */
    getMaxBonusPointsSum(): number {
        return this.studentExamWithGrade?.maxBonusPoints ?? 0;
    }

    private hasAtLeastOneResult(): boolean {
        const exercises = this.studentExamWithGrade?.studentExam?.exercises;
        if (exercises?.length! > 0) {
            return exercises!.some((exercise) => exercise.studentParticipations?.[0]?.results?.length! > 0);
        }
        return false;
    }
}
