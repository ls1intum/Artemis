import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { LectureModuleManagementComponent } from 'app/lecture/lecture-module/lecture-module-management/lecture-module-management.component';
import { NgModule } from '@angular/core';
import { lectureModuleRoute } from 'app/lecture/lecture-module/lecture-module-management/lecture-module-management.route';
import { RouterModule } from '@angular/router';
import { ModuleCreationCardComponent } from './module-creation-card/module-creation-card.component';

const ENTITY_STATES = [...lectureModuleRoute];

@NgModule({
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [LectureModuleManagementComponent, ModuleCreationCardComponent],
})
export class ArtemisLectureModuleManagementModule {}
