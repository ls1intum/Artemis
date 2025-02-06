import { NgModule } from '@angular/core';

import { ArtemisCompetenciesModule } from 'app/course/competencies/competency.module';
import { CoursePrerequisitesButtonComponent } from 'app/overview/course-registration/course-prerequisites-button/course-prerequisites-button.component';
import { CoursePrerequisitesModalModule } from 'app/overview/course-registration/course-registration-prerequisites-modal/course-prerequisites-modal.module';

@NgModule({
    imports: [ArtemisCompetenciesModule, CoursePrerequisitesModalModule, CoursePrerequisitesButtonComponent],
    exports: [CoursePrerequisitesButtonComponent],
})
export class CoursePrerequisitesButtonModule {}
