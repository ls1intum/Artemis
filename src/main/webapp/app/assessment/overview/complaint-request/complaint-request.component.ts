import { Component, input } from '@angular/core';
import { Complaint, ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormsModule } from '@angular/forms';

import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';

@Component({
    selector: 'jhi-complaint-request',
    templateUrl: './complaint-request.component.html',
    imports: [NgbTooltip, TranslateDirective, FormsModule, ArtemisTranslatePipe, ArtemisDatePipe, ArtemisTimeAgoPipe],
})
export class ComplaintRequestComponent {
    readonly complaint = input.required<Complaint>();
    readonly maxComplaintTextLimit = input.required<number>();
    readonly ComplaintType = ComplaintType;
}
