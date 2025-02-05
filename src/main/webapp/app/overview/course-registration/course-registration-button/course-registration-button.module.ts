import { NgModule } from '@angular/core';

import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisCompetenciesModule } from 'app/course/competencies/competency.module';
import { CourseRegistrationButtonComponent } from 'app/overview/course-registration/course-registration-button/course-registration-button.component';

@NgModule({
    imports: [ArtemisSharedComponentModule, ArtemisCompetenciesModule, CourseRegistrationButtonComponent],
    exports: [CourseRegistrationButtonComponent],
})
export class CourseRegistrationButtonModule {}
