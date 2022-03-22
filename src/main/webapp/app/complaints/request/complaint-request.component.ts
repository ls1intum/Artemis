import { Component, Input } from '@angular/core';
import { Complaint, ComplaintType } from 'app/entities/complaint.model';

@Component({
    selector: 'jhi-complaint-request',
    templateUrl: './complaint-request.component.html',
})
export class ComplaintRequestComponent {
    @Input() complaint: Complaint;
    @Input() maxComplaintTextLength: number;
    readonly ComplaintType = ComplaintType;
}
