import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { LearningPathProgressNavComponent } from 'app/course/learning-paths/progress-modal/learning-path-progress-nav.component';
import { LearningPathProgressModalComponent } from 'app/course/learning-paths/progress-modal/learning-path-progress-modal.component';
import { ArtemisLearningPathGraphModule } from 'app/course/learning-paths/learning-path-graph/learning-path-graph.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisLearningPathGraphModule],
    declarations: [LearningPathProgressNavComponent, LearningPathProgressModalComponent],
    exports: [LearningPathProgressModalComponent],
})
export class ArtemisLearningPathProgressModule {}
