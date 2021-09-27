import { NgModule } from '@angular/core';

import { ComplaintsComponent } from './complaints.component';
import { ClipboardModule } from 'ngx-clipboard';
import { ComplaintService } from 'app/complaints/complaint.service';
import { ComplaintInteractionsComponent } from 'app/complaints/complaint-interactions.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule, ClipboardModule],
    declarations: [ComplaintsComponent, ComplaintInteractionsComponent],
    exports: [ComplaintsComponent, ComplaintInteractionsComponent],
    providers: [ComplaintService],
})
export class ArtemisComplaintsModule {}
