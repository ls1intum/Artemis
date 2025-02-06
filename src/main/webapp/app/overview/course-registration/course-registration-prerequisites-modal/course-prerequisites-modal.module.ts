import { NgModule } from '@angular/core';

import { CoursePrerequisitesModalComponent } from 'app/overview/course-registration/course-registration-prerequisites-modal/course-prerequisites-modal.component';
import { ArtemisCompetenciesModule } from 'app/course/competencies/competency.module';

@NgModule({
    imports: [ArtemisCompetenciesModule, CoursePrerequisitesModalComponent],
})
export class CoursePrerequisitesModalModule {}
