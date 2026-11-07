import { Component, input } from '@angular/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-assessment-progress',
    templateUrl: './assessment-progress.component.html',
    host: { class: 'block' },
    imports: [TranslateDirective, ArtemisTranslatePipe],
})
export class AssessmentProgressComponent {
    readonly completed = input.required<number>();
    readonly total = input.required<number>();
    readonly assessedPercentage = input.required<number>();
    readonly inProgressPercentage = input.required<number>();
    readonly inProgress = input.required<number>();
    readonly open = input.required<number>();
}
