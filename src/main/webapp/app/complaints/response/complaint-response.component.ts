import { Component, Input } from '@angular/core';
import { Complaint, ComplaintType } from 'app/entities/complaint.model';

@Component({
    selector: 'jhi-complaint-response',
    templateUrl: './complaint-response.component.html',
})
export class ComplaintResponseComponent {
    @Input() complaint: Complaint;
    @Input() maxComplaintResponseTextLength: number;
    readonly ComplaintType = ComplaintType;
}
