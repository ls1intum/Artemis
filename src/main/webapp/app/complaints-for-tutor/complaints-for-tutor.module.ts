import { NgModule } from '@angular/core';
import { AlertService } from 'app/core/alert/alert.service';
import { MomentModule } from 'ngx-moment';
import { ClipboardModule } from 'ngx-clipboard';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ComplaintsForTutorComponent } from './complaints-for-tutor.component';
import { ComplaintResponseService } from 'app/entities/complaint-response/complaint-response.service';
import { ComplaintService } from 'app/entities/complaint/complaint.service';

@NgModule({
    imports: [ArtemisSharedModule, MomentModule, ClipboardModule],
    declarations: [ComplaintsForTutorComponent],
    exports: [ComplaintsForTutorComponent],
    providers: [ComplaintService, ComplaintResponseService],
})
export class ArtemisComplaintsForTutorModule {}
