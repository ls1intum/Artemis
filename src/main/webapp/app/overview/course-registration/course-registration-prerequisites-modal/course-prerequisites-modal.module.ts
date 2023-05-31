import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { CoursePrerequisitesModalComponent } from 'app/overview/course-registration/course-registration-prerequisites-modal/course-prerequisites-modal.component';
import { ArtemisLearningGoalsModule } from 'app/course/competencies/learning-goal.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, ArtemisLearningGoalsModule],
    declarations: [CoursePrerequisitesModalComponent],
})
export class CoursePrerequisitesModalModule {}
