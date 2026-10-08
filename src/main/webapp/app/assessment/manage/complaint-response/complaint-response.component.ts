import { Component, input } from '@angular/core';
import { Complaint, ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTimeAgoPipe } from 'app/foundation/pipes/artemis-time-ago.pipe';

@Component({
    selector: 'jhi-complaint-response',
    templateUrl: './complaint-response.component.html',
    imports: [NgbTooltip, ArtemisTranslatePipe, ArtemisDatePipe, ArtemisTimeAgoPipe],
    // The card is a cell of the grid of the complaint area, so the host must not generate a box of its own.
    host: { class: 'contents' },
})
export class ComplaintResponseComponent {
    readonly complaint = input.required<Complaint>();
    readonly maxComplaintResponseTextLimit = input.required<number>();
    readonly ComplaintType = ComplaintType;
}
