import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { ArtemisTableModule } from 'app/shared/table/table.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/manage/actions/programming-exercise-actions.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ProgrammingExerciseManageTestCasesComponent } from 'app/exercises/programming/manage/test-cases/programming-exercise-manage-test-cases.component';
import { ProgrammingExerciseManageTestCasesStatusComponent } from 'app/exercises/programming/manage/test-cases/programming-exercise-manage-test-cases-status.component';
import { ProgrammingExerciseManageTestCasesActionsComponent } from 'app/exercises/programming/manage/test-cases/programming-exercise-manage-test-cases-actions.component';
import { ProgrammingExerciseTestCasesDirtyWarningComponent } from 'app/exercises/programming/manage/test-cases/programming-exercise-test-cases-dirty-warning.component';

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
        ProgrammingExerciseManageTestCasesComponent,
        ProgrammingExerciseManageTestCasesStatusComponent,
        ProgrammingExerciseManageTestCasesActionsComponent,
        ProgrammingExerciseTestCasesDirtyWarningComponent,
    ],
    exports: [ProgrammingExerciseManageTestCasesComponent, ProgrammingExerciseTestCasesDirtyWarningComponent],
})
export class ArtemisProgrammingExerciseTestCaseModule {}
