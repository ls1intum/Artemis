import { Component, Input } from '@angular/core';
import { Complaint, ComplaintType } from 'app/entities/complaint.model';

@Component({
    selector: 'jhi-complaint-response',
    templateUrl: './complaint-response.component.html',
    styleUrls: ['../complaints.scss'],
})
export class ComplaintResponseComponent {
    @Input() complaint: Complaint;
    readonly ComplaintType = ComplaintType;
}
