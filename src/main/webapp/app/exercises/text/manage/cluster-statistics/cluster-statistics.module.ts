import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { MomentModule } from 'ngx-moment';
import { ClipboardModule } from 'ngx-clipboard';
import { clusterStatisticsRoute } from 'app/exercises/text/manage/cluster-statistics/cluster-statistics.route';
import { ClusterStatisticsComponent } from 'app/exercises/text/manage/cluster-statistics/cluster-statistics.component';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisTextSubmissionAssessmentModule } from 'app/exercises/text/assess/text-submission-assessment.module';
import { AssessmentInstructionsModule } from 'app/assessment/assessment-instructions/assessment-instructions.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';

const ENTITY_STATES = [...clusterStatisticsRoute];

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisResultModule,
        MomentModule,
        ClipboardModule,
        RouterModule.forChild(ENTITY_STATES),
        ArtemisTextSubmissionAssessmentModule,
        ArtemisAssessmentSharedModule,
        AssessmentInstructionsModule,
        ArtemisMarkdownModule,
    ],
    declarations: [ClusterStatisticsComponent],
})
export class ArtemisTextClusterStatisticsModule {}
