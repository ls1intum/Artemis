import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisLearningGoalsModule } from 'app/course/learning-goals/learning-goal.module';
import { CoursePrerequisitesButtonComponent } from 'app/overview/course-registration/course-prerequisites-button/course-prerequisites-button.component';
import { CoursePrerequisitesModalModule } from 'app/overview/course-registration/course-registration-prerequisites-modal/course-prerequisites-modal.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, ArtemisLearningGoalsModule, CoursePrerequisitesModalModule],
    declarations: [CoursePrerequisitesButtonComponent],
    exports: [CoursePrerequisitesButtonComponent],
})
export class CoursePrerequisitesButtonModule {}
