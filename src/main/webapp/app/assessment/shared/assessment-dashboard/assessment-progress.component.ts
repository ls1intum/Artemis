import { Component, computed, input } from '@angular/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';

@Component({
    selector: 'jhi-assessment-progress',
    templateUrl: './assessment-progress.component.html',
    host: { class: 'block' },
    imports: [TranslateDirective, ArtemisTranslatePipe, HelpIconComponent],
})
export class AssessmentProgressComponent {
    readonly showInProgress = input(true);
    readonly completed = input.required<number>();
    readonly total = input.required<number>();
    readonly inProgress = input.required<number>();
    readonly open = input.required<number>();

    readonly assessedPercentage = computed(() => Math.min(100, this.toPercentage(this.completed())));
    readonly inProgressPercentage = computed(() => Math.min(100 - this.assessedPercentage(), this.toPercentage(this.inProgress())));

    private toPercentage(count: number): number {
        const total = this.total();
        return total > 0 ? Math.floor((100 * count) / total) : 0;
    }
}
