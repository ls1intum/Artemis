import { Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { debounceTime, filter, finalize, map, switchMap } from 'rxjs/operators';
import { FeatureToggle } from 'app/foundation/feature-toggle/feature-toggle.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ButtonSize, ButtonType, TooltipPlacement } from 'app/shared-ui/components/buttons/button/button.component';
import { faCircleNotch, faClock, faRedo } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { NgClass } from '@angular/common';
import { ProgrammingExerciseTriggerAllButtonComponent } from '../trigger-all-button/programming-exercise-trigger-all-button.component';
import { ButtonComponent } from 'app/shared-ui/components/buttons/button/button.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { DurationPipe } from 'app/foundation/pipes/duration.pipe';
import { ExerciseSubmissionState, ProgrammingSubmissionService, ProgrammingSubmissionState } from 'app/programming/shared/services/programming-submission.service';

/**
 * This components provides two buttons to the instructor to interact with the students' submissions:
 * - Trigger builds for all student participations
 * - Trigger builds for failed student participations
 *
 * Also shows an info section next to the buttons about the number of building and failed submissions.
 */
@Component({
    selector: 'jhi-programming-exercise-instructor-submission-state',
    templateUrl: './programming-exercise-instructor-submission-state.component.html',
    imports: [FaIconComponent, NgbTooltip, NgClass, ProgrammingExerciseTriggerAllButtonComponent, ButtonComponent, ArtemisTranslatePipe, DurationPipe],
})
export class ProgrammingExerciseInstructorSubmissionStateComponent implements OnInit {
    private programmingSubmissionService = inject(ProgrammingSubmissionService);
    private destroyRef = inject(DestroyRef);

    FeatureToggle = FeatureToggle;
    ButtonType = ButtonType;
    ButtonSize = ButtonSize;
    TooltipPlacement = TooltipPlacement;
    ProgrammingSubmissionState = ProgrammingSubmissionState;

    readonly exercise = input.required<ProgrammingExercise>();
    readonly shouldToggle = input(false);
    readonly toggleBreakpoint = input<'md' | 'xl'>('xl');

    // Replaces the former constructor effect that subscribed to the submission-state stream and imperatively set three
    // signals. The building summary is now a toSignal of the exercise-id-keyed stream (switchMap re-subscribes when the
    // exercise changes and cancels the previous stream; toSignal tears it down on destroy), and the two boolean flags
    // are derived from it.
    readonly buildingSummary = toSignal(
        toObservable(computed(() => this.exercise().id)).pipe(
            filter((exerciseId): exerciseId is number => exerciseId !== undefined),
            switchMap((exerciseId) =>
                this.programmingSubmissionService.getSubmissionStateOfExercise(exerciseId).pipe(
                    map((buildState) => this.sumSubmissionStates(buildState)),
                    // If we would update the UI with every small change, it would seem very hectic. So we always take the latest value after 1 second.
                    debounceTime(500),
                ),
            ),
        ),
        { initialValue: undefined },
    );
    readonly hasFailedSubmissions = computed(() => (this.buildingSummary()?.[ProgrammingSubmissionState.HAS_FAILED_SUBMISSION] ?? 0) > 0);
    readonly hasBuildingSubmissions = computed(() => (this.buildingSummary()?.[ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION] ?? 0) > 0);
    readonly isBuildingFailedSubmissions = signal(false);

    readonly resultEtaInMs = signal<number | undefined>(undefined);

    // Icons
    faClock = faClock;
    faCircleNotch = faCircleNotch;
    faRedo = faRedo;

    ngOnInit(): void {
        this.programmingSubmissionService
            .getResultEtaInMs()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((resultEta) => this.resultEtaInMs.set(resultEta));
    }

    /**
     * Retrieve the participation ids that have a failed submission and retry their build.
     */
    triggerBuildOfFailedSubmissions() {
        this.isBuildingFailedSubmissions.set(true);
        const failedSubmissionParticipations = this.programmingSubmissionService.getSubmissionCountByType(this.exercise().id!, ProgrammingSubmissionState.HAS_FAILED_SUBMISSION);
        this.programmingSubmissionService
            .triggerInstructorBuildForParticipationsOfExercise(this.exercise().id!, failedSubmissionParticipations)
            .pipe(finalize(() => this.isBuildingFailedSubmissions.set(false)))
            .subscribe();
    }

    private sumSubmissionStates = (buildState: ExerciseSubmissionState) =>
        Object.values(buildState).reduce((acc: { [state: string]: number }, { submissionState }) => {
            return { ...acc, [submissionState]: (acc[submissionState] || 0) + 1 };
        }, {});
}
