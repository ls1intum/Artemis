import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { VerticalProgressBarComponent } from 'app/shared/vertical-progress-bar/vertical-progress-bar.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-tutorial-group-utilization-indicator',
    templateUrl: './tutorial-group-utilization-indicator.component.html',
    styleUrls: ['./tutorial-group-utilization-indicator.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [VerticalProgressBarComponent, ArtemisTranslatePipe],
})
export class TutorialGroupUtilizationIndicatorComponent {
    readonly Math = Math;
    @Input() tutorialGroup: TutorialGroup;
}
