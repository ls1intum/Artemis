import { Component, Input } from '@angular/core';
import { Complaint, ComplaintType } from 'app/entities/complaint.model';
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
    @Input() complaint: Complaint;
    @Input() maxComplaintTextLimit: number;
    readonly ComplaintType = ComplaintType;
}
