import { NgModule } from '@angular/core';

import { ComplaintsFormComponent } from 'app/complaints/form/complaints-form.component';
import { ClipboardModule } from 'ngx-clipboard';
import { ComplaintService } from 'app/complaints/complaint.service';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ComplaintStudentViewComponent } from 'app/complaints/complaints-for-students/complaint-student-view.component';
import { ComplaintRequestComponent } from 'app/complaints/request/complaint-request.component';
import { ComplaintResponseComponent } from 'app/complaints/response/complaint-response.component';

@NgModule({
    imports: [ArtemisSharedModule, ClipboardModule],
    declarations: [ComplaintsFormComponent, ComplaintStudentViewComponent, ComplaintRequestComponent, ComplaintResponseComponent],
    exports: [ComplaintStudentViewComponent],
    providers: [ComplaintService],
})
export class ArtemisComplaintsModule {}
