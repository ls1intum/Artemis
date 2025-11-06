import { Component, input } from '@angular/core';
import { Complaint, ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FormsModule } from '@angular/forms';

import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';

@Component({
    selector: 'jhi-complaint-response',
    templateUrl: './complaint-response.component.html',
    imports: [NgbTooltip, FormsModule, ArtemisTranslatePipe, ArtemisDatePipe, ArtemisTimeAgoPipe],
})
export class ComplaintResponseComponent {
    readonly complaint = input.required<Complaint>();
    readonly maxComplaintResponseTextLimit = input.required<number>();
    readonly ComplaintType = ComplaintType;
}
