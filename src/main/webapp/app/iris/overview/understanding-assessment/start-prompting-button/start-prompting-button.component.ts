import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { faBrain } from '@fortawesome/free-solid-svg-icons';
import { merge, of, switchMap, take } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ExerciseActionButtonComponent } from 'app/shared-ui/components/buttons/exercise-action-button/exercise-action-button.component';
import { FeatureToggleDirective } from 'app/foundation/feature-toggle/feature-toggle.directive';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { FeatureToggle } from 'app/foundation/feature-toggle/feature-toggle.service';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { IrisPipeEvent } from 'app/iris/shared/entities/iris-pipe-event-dto.model';

@Component({
    selector: 'jhi-start-prompting-button',
    templateUrl: './start-prompting-button.component.html',
    imports: [ExerciseActionButtonComponent, FeatureToggleDirective, ArtemisTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IrisStartPromptingButtonComponent {
    private readonly irisChatService = inject(IrisChatService);

    readonly exercise = input.required<Exercise>();
    readonly participation = input<StudentParticipation>();
    readonly smallButtons = input.required<boolean>();
    readonly hideLabelMobile = input(false);

    protected readonly FeatureToggle = FeatureToggle;
    protected readonly faBrain = faBrain;

    protected readonly isPromptingMode = signal(false);

    private readonly initialEvent$ = toObservable(this.participation).pipe(
        switchMap((participation) => {
            if (participation?.id === undefined) {
                return of(undefined);
            }

            return this.irisChatService.loadLatestEvent(participation.id);
        }),
    );

    protected readonly latestEvent = toSignal(merge(this.initialEvent$, this.irisChatService.currentLatestEvent()), { initialValue: undefined });

    protected readonly canBeStarted = computed(() => !this.isPromptingMode() && this.latestEvent() === IrisPipeEvent.BUILD_WITH_POINTS);

    protected readonly buttonLabel = computed(() => {
        if (this.canBeStarted()) {
            return 'artemisApp.exerciseActions.prompting.start';
        } else if (this.latestEvent() === IrisPipeEvent.PROMPTING_FINISHED) {
            return 'artemisApp.exerciseActions.prompting.finished';
        } else if (this.isPromptingMode()) {
            return 'artemisApp.exerciseActions.prompting.currently';
        } else {
            return 'artemisApp.exerciseActions.prompting.noSubmission';
        }
    });

    protected startPromptingMode(): void {
        if (!this.canBeStarted()) {
            return;
        }

        this.isPromptingMode.set(true);

        this.irisChatService.startPromptingMode().pipe(take(1)).subscribe();
    }

    protected readonly IrisPipeEvent = IrisPipeEvent;
}
