import { ChangeDetectorRef, Component, Input, OnChanges, OnInit } from '@angular/core';
import dayjs from 'dayjs/esm';
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
import { captureException } from '@sentry/angular-ivy';

type ExerciseInfo = {
    icon: IconProp;
    achievedPercentage?: number;
    colorClass?: string;
};

@Component({
    selector: 'jhi-exam-result-overview',
    styleUrls: ['./exam-result-overview.component.scss'],
    templateUrl: './exam-result-overview.component.html',
})
export class ExamResultOverviewComponent implements OnInit, OnChanges {
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

    constructor(
        private serverDateService: ArtemisServerDateService,
        public exerciseService: ExerciseService,
        private changeDetector: ChangeDetectorRef,
    ) {}

    ngOnInit() {
        if (this.isExamResultPublished()) {
            this.setExamGrade();
        }

        this.updateLocalVariables();
    }

    ngOnChanges() {
        this.updateLocalVariables();
    }

    private updateLocalVariables() {
        this.showResultOverview = !!(this.isExamResultPublished() && this.hasAtLeastOneResult());
        this.showIncludedInScoreColumn = this.containsExerciseThatIsNotIncludedCompletely();
        this.maxPoints = this.studentExamWithGrade?.maxPoints ?? 0;
        this.isBonusGradingKeyDisplayed = this.studentExamWithGrade.studentResult.gradeWithBonus?.bonusGrade != undefined;

        this.overallAchievedPoints = this.studentExamWithGrade?.studentResult.overallPointsAchieved ?? 0;
        this.overallAchievedPercentageRoundedByCourseSettings = roundScorePercentSpecifiedByCourseSettings(
            (this.studentExamWithGrade.studentResult.overallScoreAchieved ?? 0) / 100,
            this.studentExamWithGrade.studentExam?.exam?.course,
        );
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

    private isExamResultPublished() {
        if (this.isTestRun) {
            return true;
        }

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
}
