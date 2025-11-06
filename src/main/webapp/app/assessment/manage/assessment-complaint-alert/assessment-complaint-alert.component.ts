import { Component, input } from '@angular/core';
import { Complaint, ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';

/**
 * This shows an alert, notifying the assessor on possible complaints at the bottom of the page.
 */
@Component({
    selector: 'jhi-assessment-complaint-alert',
    templateUrl: './assessment-complaint-alert.component.html',
    styleUrls: [],
    imports: [TranslateDirective],
})
export class AssessmentComplaintAlertComponent {
    ComplaintType = ComplaintType;

    readonly complaint = input<Complaint>();
}
