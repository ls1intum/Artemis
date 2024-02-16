import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisLearningPathProgressModule } from 'app/course/learning-paths/progress-modal/learning-path-progress.module';
import { LearningPathManagementComponent } from 'app/course/learning-paths/learning-path-management/learning-path-management.component';
import { LearningPathHealthStatusWarningComponent } from 'app/course/learning-paths/learning-path-management/learning-path-health-status-warning.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, ArtemisLearningPathProgressModule],
    declarations: [LearningPathManagementComponent, LearningPathHealthStatusWarningComponent],
})
export class ArtemisLearningPathManagementModule {}
