import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { LearningPathManagementComponent } from 'app/course/learning-paths/learning-path-management/learning-path-management.component';
import { NgxGraphModule } from '@swimlane/ngx-graph';
import { ArtemisLectureUnitsModule } from 'app/overview/course-lectures/lecture-units.module';
import { ArtemisCompetenciesModule } from 'app/course/competencies/competency.module';
import { LearningPathProgressModalComponent } from 'app/course/learning-paths/learning-path-management/learning-path-progress-modal.component';
import { LearningPathGraphComponent } from 'app/course/learning-paths/learning-path-graph/learning-path-graph.component';
import { LearningPathProgressNavComponent } from 'app/course/learning-paths/learning-path-management/learning-path-progress-nav.component';
import { LearningPathGraphNodeComponent } from 'app/course/learning-paths/learning-path-graph/learning-path-graph-node.component';
import { CompetencyNodeDetailsComponent } from 'app/course/learning-paths/learning-path-graph/node-details/competency-node-details.component';
import { LectureUnitNodeDetailsComponent } from 'app/course/learning-paths/learning-path-graph/node-details/lecture-unit-node-details.component';
import { ExerciseNodeDetailsComponent } from 'app/course/learning-paths/learning-path-graph/node-details/exercise-node-details.component';

@NgModule({
    imports: [ArtemisSharedModule, FormsModule, ReactiveFormsModule, ArtemisSharedComponentModule, NgxGraphModule, ArtemisLectureUnitsModule, ArtemisCompetenciesModule],
    declarations: [
        LearningPathManagementComponent,
        LearningPathProgressModalComponent,
        LearningPathProgressNavComponent,
        LearningPathGraphComponent,
        LearningPathGraphNodeComponent,
        CompetencyNodeDetailsComponent,
        LectureUnitNodeDetailsComponent,
        ExerciseNodeDetailsComponent,
    ],
    exports: [],
})
export class ArtemisLearningPathsModule {}
