import { Component, input } from '@angular/core';
import { Message } from 'primeng/message';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { AssessmentNotPossibleYetState } from 'app/assessment/shared/util/assessment-availability.util';

/**
 * Explains on the assessment page itself why assessment of an exam exercise cannot start yet, replacing the
 * "no submission found" state the editors would otherwise show: the submission is there, the exam simply is not over.
 * <p>
 * The explanation has to stay on the page rather than being a toast, because the tutor arrives here on purpose (either
 * by URL or from the dashboard) and a toast that fades leaves the misleading empty state behind. The date is formatted
 * here so that it is shown in the browser's locale and time zone and follows language changes.
 */
@Component({
    selector: 'jhi-assessment-not-possible-yet',
    imports: [Message, TranslateDirective, ArtemisDatePipe],
    template: `
        <p-message severity="info" id="assessment-not-possible-yet" class="flex items-center gap-1 mt-4">
            <span [jhiTranslate]="reason().translationKey" [translateValues]="{ date: reason().date | artemisDate }"></span>
        </p-message>
    `,
})
export class AssessmentNotPossibleYetComponent {
    readonly reason = input.required<AssessmentNotPossibleYetState>();
}
