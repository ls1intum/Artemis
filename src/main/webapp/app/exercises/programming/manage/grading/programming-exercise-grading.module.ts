import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { ArtemisTableModule } from 'app/shared/table/table.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ProgrammingExerciseConfigureGradingComponent } from 'app/exercises/programming/manage/grading/programming-exercise-configure-grading.component';
import { ProgrammingExerciseConfigureGradingStatusComponent } from 'app/exercises/programming/manage/grading/programming-exercise-configure-grading-status.component';
import { ProgrammingExerciseConfigureGradingActionsComponent } from 'app/exercises/programming/manage/grading/programming-exercise-configure-grading-actions.component';
import { ProgrammingExerciseGradingDirtyWarningComponent } from 'app/exercises/programming/manage/grading/programming-exercise-grading-dirty-warning.component';
import { ProgrammingExerciseGradingTableActionsComponent } from 'app/exercises/programming/manage/grading/programming-exercise-grading-table-actions.component';
import { TestCasePassedBuildsGraphComponent } from 'app/exercises/programming/manage/grading/graphs/test-case-passed-builds-graph.component';
import { CategoryIssuesGraphComponent } from 'app/exercises/programming/manage/grading/graphs/category-issues-graph.component';
import { TestCaseDistributionGraphComponent } from 'app/exercises/programming/manage/grading/graphs/test-case-distribution-graph.component';
import { ScaCategoryDistributionGraphComponent } from 'app/exercises/programming/manage/grading/graphs/sca-category-distribution-graph.component';
import { TestCaseDistributionGraph2Component } from 'app/exercises/programming/manage/grading/graphs/test-case-distribution-graph2.component';

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
    ],
    declarations: [
        ProgrammingExerciseConfigureGradingComponent,
        ProgrammingExerciseConfigureGradingStatusComponent,
        ProgrammingExerciseConfigureGradingActionsComponent,
        ProgrammingExerciseGradingDirtyWarningComponent,
        ProgrammingExerciseGradingTableActionsComponent,
        TestCasePassedBuildsGraphComponent,
        CategoryIssuesGraphComponent,
        TestCaseDistributionGraphComponent,
        ScaCategoryDistributionGraphComponent,
        TestCaseDistributionGraph2Component,
    ],
    exports: [ProgrammingExerciseConfigureGradingComponent, ProgrammingExerciseGradingDirtyWarningComponent],
})
export class ArtemisProgrammingExerciseGradingModule {}
