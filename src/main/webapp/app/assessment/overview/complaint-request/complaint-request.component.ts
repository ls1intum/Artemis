import { Component, input } from '@angular/core';
import { Complaint, ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TumAetUiTagComponent } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTimeAgoPipe } from 'app/foundation/pipes/artemis-time-ago.pipe';

@Component({
    selector: 'jhi-complaint-request',
    templateUrl: './complaint-request.component.html',
    imports: [NgbTooltip, TranslateDirective, TumAetUiTagComponent, ArtemisTranslatePipe, ArtemisDatePipe, ArtemisTimeAgoPipe],
    // The card is a cell of the grid of the complaint area, so the host must not generate a box of its own.
    host: { class: 'contents' },
})
export class ComplaintRequestComponent {
    readonly complaint = input.required<Complaint>();
    readonly maxComplaintTextLimit = input.required<number>();
    readonly ComplaintType = ComplaintType;
}
