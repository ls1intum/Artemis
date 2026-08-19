import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TumUiProgressBarComponent, TumUiProgressBarSeverity, TumUiTooltipDirective } from '@tumaet/ui-angular';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { WELL_UTILIZED_PERCENTAGE, tutorialGroupUtilization } from 'app/tutorialgroup/shared/util/tutorial-group-utilization';

@Component({
    selector: 'jhi-tutorial-group-utilization-indicator',
    templateUrl: './tutorial-group-utilization-indicator.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TumUiProgressBarComponent, TumUiTooltipDirective, ArtemisTranslatePipe],
})
export class TutorialGroupUtilizationIndicatorComponent {
    readonly tutorialGroup = input.required<TutorialGroup>();

    readonly utilization = computed(() => tutorialGroupUtilization(this.tutorialGroup()));

    protected readonly severity = computed<TumUiProgressBarSeverity>(() => (this.isWellUtilized() ? 'success' : 'primary'));

    /** Inline color for the percentage readout; the number itself stays the primary signal. */
    protected readonly percentageColor = computed(() => (this.isWellUtilized() ? 'var(--success)' : undefined));

    private readonly isWellUtilized = computed(() => (this.utilization() ?? 0) >= WELL_UTILIZED_PERCENTAGE);
}
