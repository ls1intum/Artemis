import { NgModule } from '@angular/core';

import { ComplaintsRequestFormComponent } from 'app/complaints/request-form/complaints-request-form.component';
import { ComplaintsResponseFormComponent } from 'app/complaints/response-form/complaints-response-form.component';
import { ClipboardModule } from 'ngx-clipboard';
import { ComplaintService } from 'app/complaints/complaint.service';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ComplaintsStudentViewComponent } from 'app/complaints/complaints-for-students/complaints-student-view.component';
import { ComplaintRequestComponent } from 'app/complaints/request/complaint-request.component';
import { ComplaintResponseComponent } from 'app/complaints/response/complaint-response.component';

@NgModule({
    imports: [ArtemisSharedModule, ClipboardModule],
    declarations: [ComplaintsRequestFormComponent, ComplaintsResponseFormComponent, ComplaintsStudentViewComponent, ComplaintRequestComponent, ComplaintResponseComponent],
    exports: [ComplaintsStudentViewComponent, ComplaintRequestComponent, ComplaintResponseComponent, ComplaintsResponseFormComponent],
    providers: [ComplaintService],
})
export class ArtemisComplaintsModule {}
