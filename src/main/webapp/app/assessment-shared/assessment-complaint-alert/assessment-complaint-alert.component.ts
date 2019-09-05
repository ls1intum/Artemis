import { Component, OnInit, Input } from '@angular/core';
import { Complaint, ComplaintType } from 'app/entities/complaint';

@Component({
    selector: 'jhi-assessment-complaint-alert',
    templateUrl: './assessment-complaint-alert.component.html',
    styleUrls: [],
})
export class AssessmentComplaintAlertComponent {
    ComplaintType = ComplaintType;

    @Input() complaint: Complaint;
}
