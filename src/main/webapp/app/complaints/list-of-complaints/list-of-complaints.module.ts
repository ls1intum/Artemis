import { NgModule } from '@angular/core';
import { ListOfComplaintsComponent } from './list-of-complaints.component';
import { ComplaintService } from 'app/complaints/complaint.service';
import { RouterModule } from '@angular/router';
import { listOfComplaintsRoute } from 'app/complaints/list-of-complaints/list-of-complaints.route';

const ENTITY_STATES = [...listOfComplaintsRoute];

@NgModule({
    imports: [RouterModule.forChild(ENTITY_STATES), ListOfComplaintsComponent],
    exports: [ListOfComplaintsComponent],
    providers: [ComplaintService],
})
export class ArtemisListOfComplaintsModule {}
