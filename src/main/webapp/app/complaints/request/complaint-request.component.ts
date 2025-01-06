import { Component, Input } from '@angular/core';
import { Complaint, ComplaintType } from 'app/entities/complaint.model';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-complaint-request',
    templateUrl: './complaint-request.component.html',
    standalone: true,
    imports: [NgbTooltip, TranslateDirective, FormsModule, ArtemisSharedCommonModule, ArtemisTranslatePipe],
})
export class ComplaintRequestComponent {
    @Input() complaint: Complaint;
    @Input() maxComplaintTextLimit: number;
    readonly ComplaintType = ComplaintType;
}
