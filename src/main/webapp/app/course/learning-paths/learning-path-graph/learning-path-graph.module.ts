import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { NgxGraphModule } from '@swimlane/ngx-graph';
import { LearningPathGraphComponent } from 'app/course/learning-paths/learning-path-graph/learning-path-graph.component';
import { LearningPathNodeComponent } from 'app/course/learning-paths/learning-path-graph/learning-path-node.component';
import { CompetencyNodeDetailsComponent } from 'app/course/learning-paths/learning-path-graph/node-details/competency-node-details.component';
import { ExerciseNodeDetailsComponent } from 'app/course/learning-paths/learning-path-graph/node-details/exercise-node-details.component';
import { LectureUnitNodeDetailsComponent } from 'app/course/learning-paths/learning-path-graph/node-details/lecture-unit-node-details.component';
import { ArtemisCompetenciesModule } from 'app/course/competencies/competency.module';
import { LearningPathComponent } from 'app/course/learning-paths/learning-path-graph/learning-path.component';
import { LearningPathLegendComponent } from 'app/course/learning-paths/learning-path-graph/learning-path-legend.component';

@NgModule({
    imports: [ArtemisSharedModule, NgxGraphModule, ArtemisCompetenciesModule],
    declarations: [
        LearningPathGraphComponent,
        LearningPathNodeComponent,
        CompetencyNodeDetailsComponent,
        ExerciseNodeDetailsComponent,
        LectureUnitNodeDetailsComponent,
        LearningPathComponent,
        LearningPathLegendComponent,
    ],
    exports: [LearningPathGraphComponent, LearningPathComponent],
})
export class ArtemisLearningPathGraphModule {}
