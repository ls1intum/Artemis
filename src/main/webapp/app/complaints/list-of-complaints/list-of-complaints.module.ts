import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ComplaintService } from 'app/complaints/complaint.service';
import { listOfComplaintsRoute } from 'app/complaints/list-of-complaints/list-of-complaints.route';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ListOfComplaintsComponent } from './list-of-complaints.component';

const ENTITY_STATES = [...listOfComplaintsRoute];

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [ListOfComplaintsComponent],
    exports: [ListOfComplaintsComponent],
    providers: [ComplaintService],
})
export class ArtemisListOfComplaintsModule {}
