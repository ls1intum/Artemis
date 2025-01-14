import { Component, Input } from '@angular/core';
import { Complaint, ComplaintType } from 'app/entities/complaint.model';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FormsModule } from '@angular/forms';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-complaint-response',
    templateUrl: './complaint-response.component.html',
    imports: [NgbTooltip, FormsModule, ArtemisSharedCommonModule, ArtemisTranslatePipe],
})
export class ComplaintResponseComponent {
    @Input() complaint: Complaint;
    @Input() maxComplaintResponseTextLimit: number;
    readonly ComplaintType = ComplaintType;
}
