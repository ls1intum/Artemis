import { Component, ElementRef, computed, inject, input, viewChild } from '@angular/core';
import { Exercise, ExerciseType, getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { getAllResultsOfAllSubmissions } from 'app/exercise/shared/entities/submission/submission.model';
import { Popover } from 'primeng/popover';
import { ButtonModule } from 'primeng/button';
import { Tag } from 'primeng/tag';
import { faAngleDown } from '@fortawesome/free-solid-svg-icons';
import { faClock, faQuestionCircle } from '@fortawesome/free-regular-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { Badge, ResultService } from 'app/exercise/result/result.service';
import { MissingResultInformation, evaluateTemplateStatus, getResultIconClass, getTextColorClass } from 'app/exercise/result/result.utils';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Router } from '@angular/router';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ExerciseCacheService } from 'app/exercise/services/exercise-cache.service';
import { FeedbackComponent } from 'app/exercise/feedback/feedback.component';
import { prepareFeedbackComponentParameters } from 'app/exercise/feedback/feedback.utils';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { isPracticeMode } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';

@Component({
    selector: 'jhi-result-history-dropdown',
    templateUrl: './result-history-dropdown.component.html',
    imports: [Popover, ButtonModule, Tag, FaIconComponent, ArtemisDatePipe, ArtemisTranslatePipe],
})
export class ResultHistoryDropdownComponent {
    private resultService = inject(ResultService);
    private modalService = inject(NgbModal);
    private router = inject(Router);
    private exerciseService = inject(ExerciseService);
    private exerciseCacheService = inject(ExerciseCacheService, { optional: true });

    readonly faAngleDown = faAngleDown;
    readonly faClock = faClock;
    readonly ExerciseType = ExerciseType;

    exercise = input.required<Exercise>();
    sortedHistoryResults = input.required<Result[]>();
    studentParticipation = input<StudentParticipation>();

    displayedResults = computed(() => this.sortedHistoryResults());

    activeResultId = computed(() => {
        const participation = this.studentParticipation();
        if (!participation) {
            return undefined;
        }
        const results = getAllResultsOfAllSubmissions(participation.submissions);
        return results.length ? results.sort((a, b) => (b.id ?? 0) - (a.id ?? 0))[0]?.id : undefined;
    });

    resultsPopover = viewChild<Popover>('resultsPopover');
    dropdownArrow = viewChild<ElementRef>('dropdownArrow');

    toggleResultsPopover(event: Event) {
        this.resultsPopover()?.toggle(event, this.dropdownArrow()?.nativeElement);
    }

    getResultIcon(result: Result): IconProp {
        const participation = result.submission?.participation;
        if (!participation) {
            return faQuestionCircle;
        }
        const templateStatus = evaluateTemplateStatus(this.exercise(), participation, result, false, MissingResultInformation.NONE);
        return getResultIconClass(result, participation, templateStatus);
    }

    getResultColorClass(result: Result): string {
        const participation = result.submission?.participation;
        if (!participation) {
            return 'text-secondary';
        }
        const templateStatus = evaluateTemplateStatus(this.exercise(), participation, result, false, MissingResultInformation.NONE);
        return getTextColorClass(result, participation, templateStatus);
    }

    getResultText(result: Result): string {
        const participation = result.submission?.participation;
        if (!participation) {
            return '';
        }
        return this.resultService.getResultString(result, this.exercise(), participation, false);
    }

    getResultFeedbackMessage(result: Result): string {
        const submission = result.submission;
        if (submission && (submission as ProgrammingSubmission).buildFailed) {
            return 'Your build failed!';
        }

        const score = result.score ?? 0;
        if (score === 100) {
            return 'Goal reached! Excellent work.';
        }

        const sortedResults = this.sortedHistoryResults();
        const index = sortedResults.indexOf(result);
        if (index <= 0) {
            return "Nice progress! You're getting closer.";
        }

        const previousScore = sortedResults[index - 1].score ?? 0;
        if (score > previousScore) {
            return "Nice progress! You're getting closer.";
        } else if (score < previousScore) {
            return 'Oops, your score dropped. Try a different path!';
        } else {
            return 'Stuck? Try a new approach.';
        }
    }

    getBadge(result: Result): Badge {
        const participation = result.submission?.participation!;
        return ResultService.evaluateBadge(participation, result);
    }

    getBadgeSeverity(result: Result): 'success' | 'info' | 'secondary' | 'warn' | 'danger' | 'contrast' | undefined {
        const badge = this.getBadge(result);
        switch (badge.class) {
            case 'bg-success':
                return 'success';
            case 'bg-info':
                return 'info';
            case 'bg-secondary':
                return 'secondary';
            default:
                return undefined;
        }
    }

    isRowClickable(): boolean {
        const type = this.exercise().type;
        return type === ExerciseType.TEXT || type === ExerciseType.MODELING || type === ExerciseType.QUIZ;
    }

    navigateToSubmission(result: Result, event: Event) {
        event.stopPropagation();
        const participation = result.submission?.participation;
        if (!participation) {
            return;
        }
        const exercise = this.exercise();
        const courseId = getCourseFromExercise(exercise)?.id;

        if (exercise.type === ExerciseType.QUIZ) {
            const quizMode = isPracticeMode(participation) ? 'practice' : 'live';
            const queryParams = isPracticeMode(participation) ? { participationId: participation.id } : {};
            this.router.navigate(['/courses', courseId, 'exercises', 'quiz-exercises', exercise.id, quizMode], { queryParams });
            return;
        }

        const submissionId = result.submission?.id;
        const exerciseTypePath = exercise.type === ExerciseType.TEXT ? 'text-exercises' : 'modeling-exercises';
        this.router.navigate(['/courses', courseId, 'exercises', exerciseTypePath, exercise.id, 'participate', participation.id, 'submission', submissionId]);
    }

    showFeedback(result: Result, event: Event) {
        event.stopPropagation();
        const participation = result.submission?.participation;
        if (!participation) {
            return;
        }

        const exercise = this.exercise();
        const templateStatus = evaluateTemplateStatus(exercise, participation, result, false, MissingResultInformation.NONE);
        const exerciseServiceToUse = this.exerciseCacheService ?? this.exerciseService;
        const feedbackParams = prepareFeedbackComponentParameters(exercise, result, participation, templateStatus, undefined, exerciseServiceToUse);

        const modalRef = this.modalService.open(FeedbackComponent, { keyboard: true, size: 'xl' });
        const instance: FeedbackComponent = modalRef.componentInstance;
        instance.exercise = exercise;
        instance.result = result;
        instance.participation = participation;
        if (feedbackParams.exerciseType) {
            instance.exerciseType = feedbackParams.exerciseType;
        }
        if (feedbackParams.showScoreChart) {
            instance.showScoreChart = feedbackParams.showScoreChart;
        }
        if (feedbackParams.messageKey) {
            instance.messageKey = feedbackParams.messageKey;
        }
        if (feedbackParams.latestDueDate) {
            instance.latestDueDate = feedbackParams.latestDueDate;
        }
        if (feedbackParams.showMissingAutomaticFeedbackInformation) {
            instance.showMissingAutomaticFeedbackInformation = feedbackParams.showMissingAutomaticFeedbackInformation;
        }
    }
}
