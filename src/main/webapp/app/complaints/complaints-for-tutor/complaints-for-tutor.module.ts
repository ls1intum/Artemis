import { NgModule } from '@angular/core';
import { ClipboardModule } from 'ngx-clipboard';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ComplaintsForTutorComponent } from './complaints-for-tutor.component';
import { ComplaintResponseService } from 'app/complaints/complaint-response.service';
import { ComplaintService } from 'app/complaints/complaint.service';

@NgModule({
    imports: [ArtemisSharedModule, ClipboardModule],
    declarations: [ComplaintsForTutorComponent],
    exports: [ComplaintsForTutorComponent],
    providers: [ComplaintService, ComplaintResponseService],
})
export class ArtemisComplaintsForTutorModule {}
