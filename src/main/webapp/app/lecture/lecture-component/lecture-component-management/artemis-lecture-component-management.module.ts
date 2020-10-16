import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { LectureComponentManagementComponent } from 'app/lecture/lecture-component/lecture-component-management/lecture-component-management.component';
import { NgModule } from '@angular/core';
import { lectureComponentRoute } from 'app/lecture/lecture-component/lecture-component-management/lecture-component-management.route';
import { RouterModule } from '@angular/router';

const ENTITY_STATES = [...lectureComponentRoute];

@NgModule({
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [LectureComponentManagementComponent],
})
export class ArtemisLectureComponentManagementModule {}
