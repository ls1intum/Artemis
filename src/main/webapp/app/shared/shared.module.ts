import { NgModule } from '@angular/core';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { ChartComponent } from 'app/shared/chart/chart.component';
import { CircularProgressBarComponent } from 'app/shared/circular-progress-bar/circular-progress-bar.component';
import { JhiConnectionStatusComponent } from 'app/shared/connection-status/connection-status.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { DeleteDialogComponent } from 'app/shared/delete-dialog/delete-dialog.component';
import { SecureLinkDirective } from 'app/shared/http/secure-link.directive';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { ArtemisSharedPipesModule } from 'app/shared/pipes/shared-pipes.module';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisSharedLibsModule } from 'app/shared/shared-libs.module';
import { CustomMaxDirective } from 'app/shared/validators/custom-max-validator.directive';
import { CustomMinDirective } from 'app/shared/validators/custom-min-validator.directive';
import { OrganizationSelectorComponent } from './organization-selector/organization-selector.component';
import { AdditionalFeedbackComponent } from './additional-feedback/additional-feedback.component';
import { ResizeableContainerComponent } from './resizeable-container/resizeable-container.component';
import { StatisticsGraphComponent } from 'app/shared/statistics-graph/statistics-graph.component';
import { StatisticsAverageScoreGraphComponent } from 'app/shared/statistics-graph/statistics-average-score-graph.component';
import { StatisticsScoreDistributionGraphComponent } from 'app/shared/statistics-graph/statistics-score-distribution-graph.component';
import { ExerciseStatisticsComponent } from 'app/exercises/shared/statistics/exercise-statistics.component';
import { DoughnutChartComponent } from 'app/exercises/shared/statistics/doughnut-chart.component';
import { RouterModule } from '@angular/router';
import { ExerciseDetailStatisticsComponent } from 'app/exercises/shared/statistics/exercise-detail-statistics.component';
import { ExtensionPointDirective } from 'app/shared/extension-point/extension-point.directive';
import { CustomPatternValidatorDirective } from 'app/shared/validators/custom-pattern-validator.directive';
import { ItemCountComponent } from 'app/shared/pagination/item-count.component';
import { ConsistencyCheckComponent } from 'app/shared/consistency-check/consistency-check.component';
import { AssessmentWarningComponent } from 'app/assessment/assessment-warning/assessment-warning.component';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';

@NgModule({
    imports: [ArtemisSharedLibsModule, ArtemisSharedCommonModule, ArtemisSharedPipesModule, RouterModule],
    declarations: [
        CircularProgressBarComponent,
        AdditionalFeedbackComponent,
        HasAnyAuthorityDirective,
        ExtensionPointDirective,
        SecuredImageComponent,
        DeleteButtonDirective,
        DeleteDialogComponent,
        ResizeableContainerComponent,
        SecureLinkDirective,
        JhiConnectionStatusComponent,
        ChartComponent,
        OrganizationSelectorComponent,
        CustomMinDirective,
        CustomMaxDirective,
        CustomPatternValidatorDirective,
        StatisticsGraphComponent,
        StatisticsAverageScoreGraphComponent,
        StatisticsScoreDistributionGraphComponent,
        ExerciseStatisticsComponent,
        DoughnutChartComponent,
        ExerciseDetailStatisticsComponent,
        ItemCountComponent,
        ConsistencyCheckComponent,
        AssessmentWarningComponent,
        HtmlForMarkdownPipe,
    ],
    entryComponents: [DeleteDialogComponent],
    exports: [
        ArtemisSharedLibsModule,
        ArtemisSharedCommonModule,
        ArtemisSharedPipesModule,
        CircularProgressBarComponent,
        AdditionalFeedbackComponent,
        HasAnyAuthorityDirective,
        ExtensionPointDirective,
        SecuredImageComponent,
        DeleteButtonDirective,
        DeleteDialogComponent,
        ResizeableContainerComponent,
        SecureLinkDirective,
        JhiConnectionStatusComponent,
        ChartComponent,
        OrganizationSelectorComponent,
        CustomMinDirective,
        CustomMaxDirective,
        CustomPatternValidatorDirective,
        StatisticsGraphComponent,
        StatisticsAverageScoreGraphComponent,
        StatisticsScoreDistributionGraphComponent,
        ExerciseStatisticsComponent,
        DoughnutChartComponent,
        ExerciseDetailStatisticsComponent,
        ItemCountComponent,
        ConsistencyCheckComponent,
        AssessmentWarningComponent,
        HtmlForMarkdownPipe,
    ],
})
export class ArtemisSharedModule {}
