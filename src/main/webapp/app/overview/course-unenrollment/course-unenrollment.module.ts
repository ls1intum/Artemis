import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { NgModule } from '@angular/core';
import { CourseUnenrollmentModalComponent } from 'app/overview/course-unenrollment/course-unenrollment-modal.component';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule],
    declarations: [CourseUnenrollmentModalComponent],
})
export class CourseRegistrationModule {}
