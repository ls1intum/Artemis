import { Component, Input, OnDestroy, OnInit, inject } from '@angular/core';
import { faBrain } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ExerciseActionButtonComponent } from 'app/shared-ui/components/buttons/exercise-action-button/exercise-action-button.component';
import { FeatureToggleDirective } from 'app/foundation/feature-toggle/feature-toggle.directive';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { FeatureToggle } from 'app/foundation/feature-toggle/feature-toggle.service';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { EventType } from 'app/iris/shared/entities/iris-chat-websocket-dto.model';
import { Subscription } from 'rxjs';

@Component({
    selector: 'jhi-start-prompting-button',
    templateUrl: './understanding-assessment-button.component.html',
    imports: [ExerciseActionButtonComponent, FeatureToggleDirective, ArtemisTranslatePipe],
})
export class IrisUnderstandingAssessmentButtonComponent implements OnInit, OnDestroy {
    private irisChatService = inject(IrisChatService);

    readonly FeatureToggle = FeatureToggle;

    @Input()
    exercise: Exercise;
    @Input()
    smallButtons: boolean;
    @Input()
    hideLabelMobile = false;

    isEnabled = false;
    isPromptingMode = false;

    private eventSubscription: Subscription;

    // Icons
    faBrain = faBrain;

    ngOnInit() {
        this.eventSubscription = this.irisChatService.latestEvent.subscribe((event) => {
            this.isEnabled = event === EventType.BUILD_WITH_POINTS;
        });
    }

    ngOnDestroy() {
        this.eventSubscription.unsubscribe();
    }

    startPromptingMode() {
        this.isEnabled = false;
        this.isPromptingMode = !this.isPromptingMode;
        this.irisChatService.startPromptingMode().subscribe();
    }
}
