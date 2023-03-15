import { NgModule } from '@angular/core';

import { ComplaintsForTutorComponent } from './complaints-for-tutor.component';
import { ComplaintResponseService } from 'app/complaints/complaint-response.service';
import { ComplaintService } from 'app/complaints/complaint.service';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { TextareaModule } from 'app/shared/textarea/textarea.module';

@NgModule({
    imports: [ArtemisSharedModule, TextareaModule],
    declarations: [ComplaintsForTutorComponent],
    exports: [ComplaintsForTutorComponent],
    providers: [ComplaintService, ComplaintResponseService],
})
export class ArtemisComplaintsForTutorModule {}
