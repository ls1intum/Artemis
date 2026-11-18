import { Component, input } from '@angular/core';
import { TumAetUiTagComponent } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

export type CourseTutorialGroupDetailSessionStatusChipVariant = 'REGULAR' | 'CANCELLED' | 'RESCHEDULED' | 'RELOCATED';

@Component({
    selector: 'jhi-tutorial-group-detail-session-status-chip',
    imports: [TranslateDirective, TumAetUiTagComponent],
    templateUrl: './tutorial-group-detail-session-status-chip.component.html',
})
export class TutorialGroupDetailSessionStatusChipComponent {
    variant = input.required<CourseTutorialGroupDetailSessionStatusChipVariant>();
}
