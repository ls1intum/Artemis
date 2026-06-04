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
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';

@Component({
    selector: 'jhi-start-prompting-button',
    templateUrl: './start-prompting-button.component.html',
    imports: [ExerciseActionButtonComponent, FeatureToggleDirective, ArtemisTranslatePipe],
})
export class IrisStartPromptingButtonComponent implements OnInit, OnDestroy {
    private irisChatService = inject(IrisChatService);

    readonly FeatureToggle = FeatureToggle;

    @Input()
    exercise: Exercise;
    @Input()
    participation: StudentParticipation | undefined;
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
        // Initialize with latest event from Server
        if (this.participation !== undefined) {
            this.irisChatService.loadLatestEvent(this.participation.id).subscribe((event) => {
                this.isEnabled = event === EventType.BUILD_WITH_POINTS;
            });
        }

        // From now on enabled status is set by events received by websocket messages
        this.eventSubscription = this.irisChatService.currentLatestEvent().subscribe((event) => {
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
