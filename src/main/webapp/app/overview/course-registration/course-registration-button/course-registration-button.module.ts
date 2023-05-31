import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisLearningGoalsModule } from 'app/course/competencies/learning-goal.module';
import { CourseRegistrationButtonComponent } from 'app/overview/course-registration/course-registration-button/course-registration-button.component';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, ArtemisLearningGoalsModule],
    declarations: [CourseRegistrationButtonComponent],
    exports: [CourseRegistrationButtonComponent],
})
export class CourseRegistrationButtonModule {}
