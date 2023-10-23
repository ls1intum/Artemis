import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { NgxGraphModule } from '@swimlane/ngx-graph';
import { LearningPathGraphComponent } from 'app/course/learning-paths/learning-path-graph/learning-path-graph.component';
import { LearningPathGraphNodeComponent } from 'app/course/learning-paths/learning-path-graph/learning-path-graph-node.component';
import { CompetencyNodeDetailsComponent } from 'app/course/learning-paths/learning-path-graph/node-details/competency-node-details.component';
import { ExerciseNodeDetailsComponent } from 'app/course/learning-paths/learning-path-graph/node-details/exercise-node-details.component';
import { LectureUnitNodeDetailsComponent } from 'app/course/learning-paths/learning-path-graph/node-details/lecture-unit-node-details.component';
import { ArtemisCompetenciesModule } from 'app/course/competencies/competency.module';

@NgModule({
    imports: [ArtemisSharedModule, NgxGraphModule, ArtemisCompetenciesModule],
    declarations: [LearningPathGraphComponent, LearningPathGraphNodeComponent, CompetencyNodeDetailsComponent, ExerciseNodeDetailsComponent, LectureUnitNodeDetailsComponent],
    exports: [LearningPathGraphComponent],
})
export class ArtemisLearningPathGraphModule {}
