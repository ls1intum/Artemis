import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TumUiProgressBarComponent, TumUiProgressBarSeverity, TumUiTooltipDirective } from '@tumaet/ui-angular';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { UNDER_ATTENDED_PERCENTAGE, WELL_UTILIZED_PERCENTAGE, tutorialGroupUtilization } from 'app/tutorialgroup/shared/util/tutorial-group-utilization';

@Component({
    selector: 'jhi-tutorial-group-utilization-indicator',
    templateUrl: './tutorial-group-utilization-indicator.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TumUiProgressBarComponent, TumUiTooltipDirective, ArtemisTranslatePipe],
})
export class TutorialGroupUtilizationIndicatorComponent {
    readonly tutorialGroup = input.required<TutorialGroup>();

    readonly utilization = computed(() => tutorialGroupUtilization(this.tutorialGroup()));

    /** The bar alone carries the state, across three bands; the percentage beside it stays in the body text color. */
    protected readonly severity = computed<TumUiProgressBarSeverity>(() => {
        const utilization = this.utilization() ?? 0;
        if (utilization < UNDER_ATTENDED_PERCENTAGE) {
            return 'danger';
        }
        return utilization < WELL_UTILIZED_PERCENTAGE ? 'warn' : 'success';
    });
}
