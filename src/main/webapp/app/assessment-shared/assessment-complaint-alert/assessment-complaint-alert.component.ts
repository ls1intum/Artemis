import { Component, Input } from '@angular/core';
import { Complaint, ComplaintType } from 'app/entities/complaint/complaint.model';

/**
 * This shows an alert, notifying the assessor on possible complaints at the bottom of the page.
 */
@Component({
    selector: 'jhi-assessment-complaint-alert',
    templateUrl: './assessment-complaint-alert.component.html',
    styleUrls: [],
})
export class AssessmentComplaintAlertComponent {
    ComplaintType = ComplaintType;

    @Input() complaint: Complaint;
}
