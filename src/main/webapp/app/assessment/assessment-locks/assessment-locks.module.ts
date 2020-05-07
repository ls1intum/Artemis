import { NgModule } from '@angular/core';
import { AssessmentLocksComponent } from './assessment-locks.component';
import { MomentModule } from 'ngx-moment';
import { ClipboardModule } from 'ngx-clipboard';
import { RouterModule } from '@angular/router';
import { assessmentLocksRoute } from 'app/assessment/assessment-locks/assessment-locks.route';
import { SortByModule } from 'app/shared/pipes/sort-by.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

const ENTITY_STATES = [...assessmentLocksRoute];

@NgModule({
    imports: [ArtemisSharedModule, MomentModule, ClipboardModule, RouterModule.forChild(ENTITY_STATES), SortByModule],
    declarations: [AssessmentLocksComponent],
    exports: [AssessmentLocksComponent],
})
export class ArtemisAssessmentLocksModule {}
