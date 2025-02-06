import { NgModule } from '@angular/core';

import { ArtemisCompetenciesModule } from 'app/course/competencies/competency.module';
import { CourseRegistrationButtonComponent } from 'app/overview/course-registration/course-registration-button/course-registration-button.component';

@NgModule({
    imports: [ArtemisCompetenciesModule, CourseRegistrationButtonComponent],
    exports: [CourseRegistrationButtonComponent],
})
export class CourseRegistrationButtonModule {}
