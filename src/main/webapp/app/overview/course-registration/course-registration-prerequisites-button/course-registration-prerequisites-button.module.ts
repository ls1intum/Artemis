import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisLearningGoalsModule } from 'app/course/learning-goals/learning-goal.module';
import { CourseRegistrationPrerequisitesButtonComponent } from 'app/overview/course-registration/course-registration-prerequisites-button/course-registration-prerequisites-button.component';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, ArtemisLearningGoalsModule],
    declarations: [CourseRegistrationPrerequisitesButtonComponent],
    exports: [CourseRegistrationPrerequisitesButtonComponent],
})
export class CourseRegistrationPrerequisitesButtonModule {}
