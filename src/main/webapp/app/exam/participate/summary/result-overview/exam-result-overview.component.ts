import { ChangeDetectorRef, Component, Input, OnInit } from '@angular/core';
import dayjs from 'dayjs/esm';
import { Exercise, IncludedInOverallScore, getIcon } from 'app/entities/exercise.model';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { GradeType } from 'app/entities/grading-scale.model';
import { faAward, faClipboard } from '@fortawesome/free-solid-svg-icons';
import { ExerciseResult, StudentExamWithGradeDTO } from 'app/exam/exam-scores/exam-score-dtos.model';
import { BonusStrategy } from 'app/entities/bonus.model';
import { evaluateTemplateStatus, getTextColorClass } from 'app/exercises/shared/result/result.utils';
import { faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { roundScorePercentSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { getLatestResultOfStudentParticipation } from 'app/exercises/shared/participation/participation.utils';

@Component({
    selector: 'jhi-exam-result-overview',
    styleUrls: ['./exam-result-overview.component.scss'],
    templateUrl: './exam-result-overview.component.html',
})
export class ExamResultOverviewComponent implements OnInit {
    readonly IncludedInOverallScore = IncludedInOverallScore;
    readonly BonusStrategy = BonusStrategy;

    @Input() studentExamWithGrade: StudentExamWithGradeDTO;
    @Input() isGradingKeyCollapsed: boolean = true;
    @Input() isBonusGradingKeyCollapsed: boolean = true;

    gradingScaleExists = false;
    isBonus = false;
    hasPassed = false;
    grade?: string;

    // Icons
    faClipboard = faClipboard;
    faAward = faAward;
    faChevronRight = faChevronRight;

    constructor(
        private serverDateService: ArtemisServerDateService,
        public exerciseService: ExerciseService,
        private changeDetector: ChangeDetectorRef,
    ) {}

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
    showResultOverview(): boolean {
        return !!(this.isExamResultPublished() && this.hasAtLeastOneResult());
    }

    /**
     * If all exercises are included in the overall score, we do not need to show the column
     * -> displayed if at least one exercise is not included in the overall score
     */
    showIncludedInScoreColumn(): boolean {
        for (const exercise of this.studentExamWithGrade?.studentExam?.exercises ?? []) {
            if (exercise.includedInOverallScore !== IncludedInOverallScore.INCLUDED_COMPLETELY) {
                return true;
            }
        }

        return false;
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

    /**
     * Returns the max. achievable (normal) points. It is possible to exceed this value if there are bonus points.
     */
    getMaxNormalPointsSum() {
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

    /**
     * Returns the sum of max. achievable normal and bonus points. It is not possible to exceed this value.
     */
    getMaxNormalAndBonusPointsSum(): number {
        return this.getMaxNormalPointsSum() + this.getMaxBonusPointsSum();
    }

    private getExerciseResultByExerciseId(exerciseId?: number): ExerciseResult | undefined {
        if (exerciseId === undefined) {
            return undefined;
        }

        const exerciseGroupResultMapping = this.studentExamWithGrade?.studentResult?.exerciseGroupIdToExerciseResult;
        let exerciseResult = undefined;

        for (const key in exerciseGroupResultMapping) {
            if (key in exerciseGroupResultMapping && exerciseGroupResultMapping[key].exerciseId === exerciseId) {
                exerciseResult = exerciseGroupResultMapping[key];
                break;
            }
        }

        return exerciseResult;
    }

    getAchievedPercentageByExerciseId(exerciseId?: number): number | undefined {
        const result = this.getExerciseResultByExerciseId(exerciseId);
        if (result === undefined) {
            return undefined;
        }

        const course = this.studentExamWithGrade.studentExam?.exam?.course;

        if (result.achievedScore !== undefined) {
            return roundScorePercentSpecifiedByCourseSettings(result.achievedScore / 100, course);
        }

        const canCalculatePercentage = result.maxScore && result.achievedPoints !== undefined;
        if (canCalculatePercentage) {
            return roundScorePercentSpecifiedByCourseSettings(result.achievedPoints! / result.maxScore, course);
        }

        return undefined;
    }

    scrollToExercise(exerciseId?: number) {
        if (exerciseId === undefined) {
            return;
        }

        const searchedId = `exercise-${exerciseId}`;
        const targetElement = document.getElementById(searchedId);

        if (targetElement) {
            targetElement.scrollIntoView({
                behavior: 'smooth',
                block: 'start',
                inline: 'nearest',
            });
        } else {
            console.error(`Could not find corresponding exercise with id "${searchedId}"`);
        }
    }

    private hasAtLeastOneResult(): boolean {
        const exercises = this.studentExamWithGrade?.studentExam?.exercises;
        // eslint-disable-next-line @typescript-eslint/no-non-null-asserted-optional-chain
        if (exercises?.length! > 0) {
            // eslint-disable-next-line @typescript-eslint/no-non-null-asserted-optional-chain
            return exercises!.some((exercise) => exercise.studentParticipations?.[0]?.results?.length! > 0);
        }
        return false;
    }

    getTextColorClassByExercise(exercise: Exercise) {
        const participation = exercise.studentParticipations![0];
        const showUngradedResults = false;
        const result = getLatestResultOfStudentParticipation(participation, showUngradedResults);

        const isBuilding = false;
        const templateStatus = evaluateTemplateStatus(exercise, participation, result, isBuilding);

        return getTextColorClass(result, templateStatus);
    }

    toggleGradingKey(): void {
        this.isGradingKeyCollapsed = !this.isGradingKeyCollapsed;
    }

    toggleBonusGradingKey(): void {
        this.isBonusGradingKeyCollapsed = !this.isBonusGradingKeyCollapsed;
    }

    isBonusGradingKeyDisplayed(): boolean {
        return this.studentExamWithGrade.studentResult.gradeWithBonus?.bonusGrade != undefined;
    }

    protected readonly getIcon = getIcon;
    protected readonly getTextColorClass = getTextColorClass;
    protected readonly evaluateTemplateStatus = evaluateTemplateStatus;
}
