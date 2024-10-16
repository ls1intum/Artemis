import { ChangeDetectorRef, Component, Input, OnChanges, OnInit, inject } from '@angular/core';
import { IncludedInOverallScore } from 'app/entities/exercise.model';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { GradeType } from 'app/entities/grading-scale.model';
import { faAward, faClipboard } from '@fortawesome/free-solid-svg-icons';
import { StudentExamWithGradeDTO } from 'app/exam/exam-scores/exam-score-dtos.model';
import { BonusStrategy } from 'app/entities/bonus.model';
import { faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { roundScorePercentSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { captureException } from '@sentry/angular';
import { isExamResultPublished } from 'app/exam/participate/exam.utils';

type ExerciseInfo = {
    icon: IconProp;
    achievedPercentage?: number;
    colorClass?: string;
};

type ResultOverviewSection = 'grading-table' | 'grading-key' | 'bonus-grading-key';

@Component({
    selector: 'jhi-exam-result-overview',
    styleUrls: ['./exam-result-overview.component.scss'],
    templateUrl: './exam-result-overview.component.html',
})
export class ExamResultOverviewComponent implements OnInit, OnChanges {
    private serverDateService = inject(ArtemisServerDateService);
    exerciseService = inject(ExerciseService);
    private changeDetector = inject(ChangeDetectorRef);

    readonly IncludedInOverallScore = IncludedInOverallScore;
    readonly BonusStrategy = BonusStrategy;

    @Input() studentExamWithGrade: StudentExamWithGradeDTO;
    @Input() isGradingKeyCollapsed: boolean = true;
    @Input() isBonusGradingKeyCollapsed: boolean = true;
    @Input() exerciseInfos: Record<number, ExerciseInfo>;
    @Input() isTestRun: boolean = false;

    gradingScaleExists = false;
    isBonus = false;
    hasPassed = false;
    grade?: string;

    // Icons
    faClipboard = faClipboard;
    faAward = faAward;
    faChevronRight = faChevronRight;

    showIncludedInScoreColumn = false;
    /**
     * the max. achievable (normal) points. It is possible to exceed this value if there are bonus points.
     */
    maxPoints = 0;
    overallAchievedPoints = 0;
    overallAchievedPercentageRoundedByCourseSettings = 0;
    isBonusGradingKeyDisplayed = false;

    /**
     * The points summary table will only be shown if:
     * - exam.publishResultsDate is set
     * - we are after the exam.publishResultsDate
     * - at least one exercise has a result
     * - it is a test run (results are published immediately)
     */
    showResultOverview = false;

    isCollapsed: Record<ResultOverviewSection, boolean> = {
        'grading-table': false,
        'grading-key': true,
        'bonus-grading-key': true,
    };

    ngOnInit() {
        if (this.areResultsPublished()) {
            this.setExamGrade();
        }

        this.updateLocalVariables();
    }

    ngOnChanges() {
        this.updateLocalVariables();
    }

    private areResultsPublished() {
        return isExamResultPublished(this.isTestRun, this.studentExamWithGrade?.studentExam?.exam, this.serverDateService);
    }

    private updateLocalVariables() {
        this.showResultOverview = !!(this.areResultsPublished() && this.hasAtLeastOneResult());
        this.showIncludedInScoreColumn = this.containsExerciseThatIsNotIncludedCompletely();
        this.maxPoints = this.studentExamWithGrade?.maxPoints ?? 0;
        this.isBonusGradingKeyDisplayed = this.studentExamWithGrade.studentResult.gradeWithBonus?.bonusGrade != undefined;

        this.overallAchievedPoints = this.getOverallAchievedPoints();
        this.overallAchievedPercentageRoundedByCourseSettings = this.getOverallAchievedPercentageRoundedByCourseSettings();
    }

    /**
     * used as fallback if not pre-calculated by the server
     */
    private sumExerciseScores() {
        return (this.studentExamWithGrade.studentExam?.exercises ?? []).reduce((exerciseScoreSum, exercise) => {
            const achievedPoints = this.studentExamWithGrade?.achievedPointsPerExercise?.[exercise.id!] ?? 0;
            return exerciseScoreSum + achievedPoints;
        }, 0);
    }

    private getOverallAchievedPoints() {
        const overallAchievedPoints = this.studentExamWithGrade?.studentResult.overallPointsAchieved;
        if (overallAchievedPoints === undefined || overallAchievedPoints === 0) {
            return this.sumExerciseScores();
        }

        return overallAchievedPoints;
    }

    private getOverallAchievedPercentageRoundedByCourseSettings() {
        let overallScoreAchieved = this.studentExamWithGrade.studentResult.overallScoreAchieved;
        if (overallScoreAchieved === undefined || overallScoreAchieved === 0) {
            overallScoreAchieved = this.summedAchievedExerciseScorePercentage();
        }

        return roundScorePercentSpecifiedByCourseSettings(overallScoreAchieved / 100, this.studentExamWithGrade.studentExam?.exam?.course);
    }

    /**
     * used as fallback if not pre-calculated by the server
     */
    private summedAchievedExerciseScorePercentage() {
        let summedPercentages = 0;
        let numberOfExercises = 0;

        Object.entries(this.exerciseInfos).forEach(([, exerciseInfo]) => {
            summedPercentages += exerciseInfo.achievedPercentage ?? 0;
            numberOfExercises++;
        });

        if (numberOfExercises === 0) {
            return 0;
        }

        return summedPercentages / numberOfExercises;
    }

    /**
     * If all exercises are included in the overall score, we do not need to show the column
     * -> displayed if at least one exercise is not included in the overall score
     */
    containsExerciseThatIsNotIncludedCompletely(): boolean {
        for (const exercise of this.studentExamWithGrade?.studentExam?.exercises ?? []) {
            if (exercise.includedInOverallScore !== IncludedInOverallScore.INCLUDED_COMPLETELY) {
                return true;
            }
        }

        return false;
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

    /**
     * Returns the sum of max. achievable normal and bonus points. It is not possible to exceed this value.
     */
    getMaxNormalAndBonusPointsSum(): number {
        const maxAchievableBonusPoints = this.studentExamWithGrade?.maxBonusPoints ?? 0;
        return this.maxPoints + maxAchievableBonusPoints;
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
            const errorMessage = 'Cannot scroll to exercise, could not find exercise with corresponding id';
            console.error(errorMessage);
            captureException(new Error(errorMessage), {
                extra: {
                    exerciseId,
                    searchedId,
                    targetElement,
                },
            });
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

    toggleGradingKey(): void {
        this.isGradingKeyCollapsed = !this.isGradingKeyCollapsed;
    }

    toggleBonusGradingKey(): void {
        this.isBonusGradingKeyCollapsed = !this.isBonusGradingKeyCollapsed;
    }

    toggleCollapse(resultOverviewSection: ResultOverviewSection) {
        return () => (this.isCollapsed[resultOverviewSection] = !this.isCollapsed[resultOverviewSection]);
    }
}
