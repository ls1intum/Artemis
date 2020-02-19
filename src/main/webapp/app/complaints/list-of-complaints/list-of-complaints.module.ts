import { NgModule } from '@angular/core';
import { AlertService } from 'app/core/alert/alert.service';
import { ListOfComplaintsComponent } from './list-of-complaints.component';
import { MomentModule } from 'ngx-moment';
import { ClipboardModule } from 'ngx-clipboard';
import { ComplaintService } from 'app/complaints/complaint.service';
import { RouterModule } from '@angular/router';
import { listOfComplaintsRoute } from 'app/complaints/list-of-complaints/list-of-complaints.route';
import { SortByModule } from 'app/shared/pipes/sort-by.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

const ENTITY_STATES = [...listOfComplaintsRoute];

@NgModule({
    imports: [ArtemisSharedModule, MomentModule, ClipboardModule, RouterModule.forChild(ENTITY_STATES), SortByModule],
    declarations: [ListOfComplaintsComponent],
    exports: [ListOfComplaintsComponent],
    providers: [ComplaintService],
})
export class ArtemisListOfComplaintsModule {}
