import { Component, ElementRef, computed, effect, inject, input, output, signal, viewChild } from '@angular/core';
import { Exercise, ExerciseType, getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Popover } from 'primeng/popover';
import { ButtonModule } from 'primeng/button';
import { Tag } from 'primeng/tag';
import { faAngleDown } from '@fortawesome/free-solid-svg-icons';
import { faClock, faQuestionCircle } from '@fortawesome/free-regular-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { Badge, ResultService } from 'app/exercise/result/result.service';
import { MissingResultInformation, evaluateTemplateStatus, getResultIconClass, getTextColorClass } from 'app/exercise/result/result.utils';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { NavigationEnd, Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs/operators';
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
    styleUrls: ['./result-history-dropdown.component.scss'],
    imports: [Popover, ButtonModule, Tag, FaIconComponent, ArtemisDatePipe, ArtemisTranslatePipe, TranslateDirective],
})
export class ResultHistoryDropdownComponent {
    private resultService = inject(ResultService);
    private translateService = inject(TranslateService);
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
    showUngradedResults = input<boolean>(false);

    displayedResults = computed(() => [...this.sortedHistoryResults()]);

    private readonly selectedResultId = signal<number | undefined>(undefined);

    private readonly latestResultId = computed(() => {
        const participation = this.studentParticipation();
        const allResults = participation?.submissions?.flatMap((s) => s.results ?? []) ?? [];
        if (!allResults.length) {
            return undefined;
        }
        return allResults.reduce((maxId, result) => Math.max(maxId, result.id ?? 0), 0) || undefined;
    });

    activeResultId = computed(() => {
        return this.selectedResultId() ?? this.latestResultId();
    });

    isViewingSubmission = computed(() => {
        return this.selectedResultId() !== undefined;
    });

    viewingSubmissionChange = output<boolean>();

    constructor() {
        effect(() => {
            this.studentParticipation();
            this.syncSelectedResultWithRoute();
        });

        // Also sync on navigation events (URL changes without signal changes)
        this.router.events
            .pipe(
                filter((event): event is NavigationEnd => event instanceof NavigationEnd),
                takeUntilDestroyed(),
            )
            .subscribe(() => this.syncSelectedResultWithRoute());
    }

    private syncSelectedResultWithRoute() {
        const resultMatch = this.router.url.match(/\/result\/(\d+)/);
        if (resultMatch) {
            const resultId = Number(resultMatch[1]);
            const matchingResult = this.sortedHistoryResults().find((r) => r.id === resultId);
            if (matchingResult?.id) {
                this.selectedResultId.set(matchingResult.id);
                this.viewingSubmissionChange.emit(true);
                return;
            }
        }
        const match = this.router.url.match(/\/submission\/(\d+)/);
        if (match) {
            const submissionId = Number(match[1]);
            const matchingResult = this.sortedHistoryResults().find((r) => r.submission?.id === submissionId);
            if (matchingResult?.id) {
                this.selectedResultId.set(matchingResult.id);
                this.viewingSubmissionChange.emit(true);
                return;
            }
        }
        this.selectedResultId.set(undefined);
        this.viewingSubmissionChange.emit(false);
    }

    continueToLatest() {
        this.selectedResultId.set(undefined);
        this.viewingSubmissionChange.emit(false);
        const participation = this.studentParticipation();
        if (!participation) {
            return;
        }
        const exercise = this.exercise();
        const courseId = getCourseFromExercise(exercise)?.id;

        if (exercise.type === ExerciseType.QUIZ) {
            if (isPracticeMode(participation)) {
                this.router.navigate(['/courses', courseId, 'exercises', 'quiz-exercises', exercise.id, 'practice', participation.id]);
            } else {
                this.router.navigate(['/courses', courseId, 'exercises', 'quiz-exercises', exercise.id, 'live']);
            }
            return;
        }

        const exerciseTypePath = exercise.type === ExerciseType.TEXT ? 'text-exercises' : 'modeling-exercises';
        this.router.navigate(['/courses', courseId, 'exercises', exerciseTypePath, exercise.id, 'participate', participation.id]);
    }

    resultsPopover = viewChild<Popover>('resultsPopover');
    dropdownArrow = viewChild<ElementRef>('dropdownArrow');

    toggleResultsPopover(event: Event) {
        const popover = this.resultsPopover();
        if (popover?.overlayVisible) {
            popover.hide();
        } else {
            popover?.show(event, this.dropdownArrow()?.nativeElement);
        }
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
            return this.translateService.instant('artemisApp.result.progressString.buildFailed');
        }

        const score = result.score ?? 0;
        if (score === 100) {
            return this.translateService.instant('artemisApp.result.progressString.goalReached');
        }

        const sortedResults = this.sortedHistoryResults();
        const index = sortedResults.indexOf(result);
        const previousScore = index > 0 ? sortedResults[index - 1].score : undefined;
        if (previousScore === undefined) {
            return this.translateService.instant(score > 0 ? 'artemisApp.result.progressString.niceProgress' : 'artemisApp.result.progressString.stuck');
        }
        if (score > previousScore) {
            return this.translateService.instant('artemisApp.result.progressString.niceProgress');
        } else if (score < previousScore) {
            return this.translateService.instant('artemisApp.result.progressString.scoreDrop');
        } else {
            return this.translateService.instant('artemisApp.result.progressString.stuck');
        }
    }

    getBadge(result: Result): Badge {
        const participation = result.submission?.participation ?? this.studentParticipation();
        if (!participation) {
            return { class: 'bg-secondary', text: '', tooltip: '' };
        }
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
        this.selectedResultId.set(result.id);
        this.viewingSubmissionChange.emit(true);
        this.resultsPopover()?.hide();
        const exercise = this.exercise();
        const courseId = getCourseFromExercise(exercise)?.id;

        if (exercise.type === ExerciseType.QUIZ) {
            if (isPracticeMode(participation)) {
                const submissionId = result.submission?.id;
                this.router.navigate(['/courses', courseId, 'exercises', 'quiz-exercises', exercise.id, 'practice', participation.id, 'submission', submissionId]);
            } else {
                this.router.navigate(['/courses', courseId, 'exercises', 'quiz-exercises', exercise.id, 'live']);
            }
            return;
        }

        const submissionId = result.submission?.id;
        const exerciseTypePath = exercise.type === ExerciseType.TEXT ? 'text-exercises' : 'modeling-exercises';
        this.router.navigate(['/courses', courseId, 'exercises', exerciseTypePath, exercise.id, 'participate', participation.id, 'submission', submissionId, 'result', result.id]);
    }

    showFeedback(result: Result, event: Event) {
        event.stopPropagation();
        const participation = result.submission?.participation;
        if (!participation) {
            return;
        }
        this.selectedResultId.set(result.id);

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
