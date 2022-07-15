import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { ArtemisTableModule } from 'app/shared/table/table.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ProgrammingExerciseConfigureGradingComponent } from 'app/exercises/programming/manage/grading/programming-exercise-configure-grading.component';
import { ProgrammingExerciseConfigureGradingStatusComponent } from 'app/exercises/programming/manage/grading/programming-exercise-configure-grading-status.component';
import { ProgrammingExerciseConfigureGradingActionsComponent } from 'app/exercises/programming/manage/grading/programming-exercise-configure-grading-actions.component';
import { ProgrammingExerciseGradingDirtyWarningComponent } from 'app/exercises/programming/manage/grading/programming-exercise-grading-dirty-warning.component';
import { ProgrammingExerciseGradingTableActionsComponent } from 'app/exercises/programming/manage/grading/programming-exercise-grading-table-actions.component';
import { TestCasePassedBuildsChartComponent } from 'app/exercises/programming/manage/grading/charts/test-case-passed-builds-chart.component';
import { CategoryIssuesChartComponent } from 'app/exercises/programming/manage/grading/charts/category-issues-chart.component';
import { TestCaseDistributionChartComponent } from 'app/exercises/programming/manage/grading/charts/test-case-distribution-chart.component';
import { ScaCategoryDistributionChartComponent } from 'app/exercises/programming/manage/grading/charts/sca-category-distribution-chart.component';
import { ProgrammingExerciseGradingSubmissionPolicyConfigurationActionsComponent } from 'app/exercises/programming/manage/grading/programming-exercise-grading-submission-policy-configuration-actions.component';
import { SubmissionPolicyUpdateModule } from 'app/exercises/shared/submission-policy/submission-policy-update.module';
import { BarChartModule } from '@swimlane/ngx-charts';

@NgModule({
    imports: [
        // external dependencies.
        NgxDatatableModule,
        RouterModule,
        // shared modules.
        ArtemisSharedModule,
        ArtemisSharedComponentModule,
        ArtemisTableModule,
        // programming exercise sub modules.
        ArtemisProgrammingExerciseActionsModule,
        SubmissionPolicyUpdateModule,
        BarChartModule,
    ],
    declarations: [
        ProgrammingExerciseConfigureGradingComponent,
        ProgrammingExerciseConfigureGradingStatusComponent,
        ProgrammingExerciseConfigureGradingActionsComponent,
        ProgrammingExerciseGradingDirtyWarningComponent,
        ProgrammingExerciseGradingTableActionsComponent,
        ProgrammingExerciseGradingSubmissionPolicyConfigurationActionsComponent,
        TestCasePassedBuildsChartComponent,
        CategoryIssuesChartComponent,
        TestCaseDistributionChartComponent,
        ScaCategoryDistributionChartComponent,
    ],
    exports: [ProgrammingExerciseConfigureGradingComponent, ProgrammingExerciseGradingDirtyWarningComponent],
})
export class ArtemisProgrammingExerciseGradingModule {}
