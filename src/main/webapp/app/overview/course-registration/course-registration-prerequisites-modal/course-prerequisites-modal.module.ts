import { NgModule } from '@angular/core';

import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { CoursePrerequisitesModalComponent } from 'app/overview/course-registration/course-registration-prerequisites-modal/course-prerequisites-modal.component';
import { ArtemisCompetenciesModule } from 'app/course/competencies/competency.module';

@NgModule({
    imports: [ArtemisSharedComponentModule, ArtemisCompetenciesModule, CoursePrerequisitesModalComponent],
})
export class CoursePrerequisitesModalModule {}
