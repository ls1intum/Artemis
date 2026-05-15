import { Component, OnInit, effect, inject, input, signal } from '@angular/core';
import { debounceTime, map, tap } from 'rxjs/operators';
import { Subscription } from 'rxjs';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ButtonSize, ButtonType, TooltipPlacement } from 'app/shared/components/buttons/button/button.component';
import { faCircleNotch, faClock, faRedo } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { NgClass } from '@angular/common';
import { ProgrammingExerciseTriggerAllButtonComponent } from '../trigger-all-button/programming-exercise-trigger-all-button.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { DurationPipe } from 'app/shared/pipes/duration.pipe';
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

    FeatureToggle = FeatureToggle;
    ButtonType = ButtonType;
    ButtonSize = ButtonSize;
    TooltipPlacement = TooltipPlacement;
    ProgrammingSubmissionState = ProgrammingSubmissionState;

    readonly exercise = input.required<ProgrammingExercise>();
    readonly shouldToggle = input(false);
    readonly toggleBreakpoint = input<'md' | 'xl'>('xl');

    readonly hasFailedSubmissions = signal(false);
    readonly hasBuildingSubmissions = signal(false);
    readonly buildingSummary = signal<{ [submissionState: string]: number } | undefined>(undefined);
    readonly isBuildingFailedSubmissions = signal(false);

    readonly resultEtaInMs = signal<number | undefined>(undefined);

    submissionStateSubscription: Subscription;
    resultEtaSubscription: Subscription;

    private lastSubscribedExerciseId: number | undefined;

    // Icons
    faClock = faClock;
    faCircleNotch = faCircleNotch;
    faRedo = faRedo;

    constructor() {
        effect(() => {
            const exercise = this.exercise();
            const exerciseId = exercise?.id;
            if (exerciseId !== undefined && exerciseId !== this.lastSubscribedExerciseId) {
                this.lastSubscribedExerciseId = exerciseId;
                this.submissionStateSubscription?.unsubscribe();
                this.submissionStateSubscription = this.programmingSubmissionService
                    .getSubmissionStateOfExercise(exerciseId)
                    .pipe(
                        map(this.sumSubmissionStates),
                        // If we would update the UI with every small change, it would seem very hectic. So we always take the latest value after 1 second.
                        debounceTime(500),
                        tap((buildingSummary: { [submissionState: string]: number }) => {
                            this.buildingSummary.set(buildingSummary);
                            this.hasFailedSubmissions.set((buildingSummary[ProgrammingSubmissionState.HAS_FAILED_SUBMISSION] ?? 0) > 0);
                            this.hasBuildingSubmissions.set((buildingSummary[ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION] ?? 0) > 0);
                        }),
                    )
                    .subscribe();
            }
        });
    }

    ngOnInit(): void {
        this.resultEtaSubscription = this.programmingSubmissionService.getResultEtaInMs().subscribe((resultEta) => this.resultEtaInMs.set(resultEta));
    }

    /**
     * Retrieve the participation ids that have a failed submission and retry their build.
     */
    triggerBuildOfFailedSubmissions() {
        this.isBuildingFailedSubmissions.set(true);
        const failedSubmissionParticipations = this.programmingSubmissionService.getSubmissionCountByType(this.exercise().id!, ProgrammingSubmissionState.HAS_FAILED_SUBMISSION);
        this.programmingSubmissionService
            .triggerInstructorBuildForParticipationsOfExercise(this.exercise().id!, failedSubmissionParticipations)
            .subscribe(() => this.isBuildingFailedSubmissions.set(false));
    }

    private sumSubmissionStates = (buildState: ExerciseSubmissionState) =>
        Object.values(buildState).reduce((acc: { [state: string]: number }, { submissionState }) => {
            return { ...acc, [submissionState]: (acc[submissionState] || 0) + 1 };
        }, {});
}
