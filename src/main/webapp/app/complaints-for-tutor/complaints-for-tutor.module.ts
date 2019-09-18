import { NgModule } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';
import { MomentModule } from 'ngx-moment';
import { ClipboardModule } from 'ngx-clipboard';
import { ArtemisSharedModule } from 'app/shared';
import { ComplaintsForTutorComponent } from './complaints-for-tutor.component';
import { ComplaintService } from 'app/entities/complaint';
import { ComplaintResponseService } from 'app/entities/complaint-response';

@NgModule({
    imports: [ArtemisSharedModule, MomentModule, ClipboardModule],
    declarations: [ComplaintsForTutorComponent],
    exports: [ComplaintsForTutorComponent],
    providers: [JhiAlertService, ComplaintService, ComplaintResponseService],
})
export class ArtemisComplaintsForTutorModule {}
