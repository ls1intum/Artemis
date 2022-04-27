import { NgModule } from '@angular/core';

import { ComplaintsFormComponent } from 'app/complaints/form/complaints-form.component';
import { ComplaintService } from 'app/complaints/complaint.service';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ComplaintsStudentViewComponent } from 'app/complaints/complaints-for-students/complaints-student-view.component';
import { ComplaintRequestComponent } from 'app/complaints/request/complaint-request.component';
import { ComplaintResponseComponent } from 'app/complaints/response/complaint-response.component';
import { TextareaModule } from 'app/shared/textarea/textarea.module';

@NgModule({
    imports: [ArtemisSharedModule, TextareaModule],
    declarations: [ComplaintsFormComponent, ComplaintsStudentViewComponent, ComplaintRequestComponent, ComplaintResponseComponent],
    exports: [ComplaintsStudentViewComponent],
    providers: [ComplaintService],
})
export class ArtemisComplaintsModule {}
