import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { VerticalProgressBarComponent } from 'app/tutorialgroup/manage/vertical-progress-bar/vertical-progress-bar.component';

@Component({
    selector: 'jhi-tutorial-group-utilization-indicator',
    templateUrl: './tutorial-group-utilization-indicator.component.html',
    styleUrls: ['./tutorial-group-utilization-indicator.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [VerticalProgressBarComponent, ArtemisTranslatePipe],
})
export class TutorialGroupUtilizationIndicatorComponent {
    readonly Math = Math;
    tutorialGroup = input.required<TutorialGroup>();
}
