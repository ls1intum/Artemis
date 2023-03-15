import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';

@Component({
    selector: 'jhi-tutorial-group-utilization-indicator',
    templateUrl: './tutorial-group-utilization-indicator.component.html',
    styleUrls: ['./tutorial-group-utilization-indicator.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TutorialGroupUtilizationIndicatorComponent {
    readonly Math = Math;
    @Input() tutorialGroup: TutorialGroup;
}
