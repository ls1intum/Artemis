import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { LectureUnitManagementComponent } from 'app/lecture/lecture-unit/lecture-module-management/lecture-unit-management.component';
import { NgModule } from '@angular/core';
import { lectureUnitRoute } from 'app/lecture/lecture-unit/lecture-module-management/lecture-unit-management.route';
import { RouterModule } from '@angular/router';
import { UnitCreationCardComponent } from './unit-creation-card/unit-creation-card.component';

const ENTITY_STATES = [...lectureUnitRoute];

@NgModule({
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [LectureUnitManagementComponent, UnitCreationCardComponent],
})
export class ArtemisLectureUnitManagementModule {}
