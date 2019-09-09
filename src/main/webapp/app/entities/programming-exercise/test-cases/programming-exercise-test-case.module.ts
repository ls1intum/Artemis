import { NgModule } from '@angular/core';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { ProgrammingExerciseManageTestCasesActionsComponent, ProgrammingExerciseManageTestCasesComponent, ProgrammingExerciseManageTestCasesStatusComponent } from './';
import { ArtemisTableModule } from 'app/components/table/table.module';
import { ArtemisSharedModule } from 'app/shared';
import { ArtemisProgrammingExerciseActionsModule } from 'app/entities/programming-exercise/actions/programming-exercise-actions.module';

@NgModule({
    imports: [
        // external dependencies.
        NgxDatatableModule,
        // shared modules.
        ArtemisSharedModule,
        ArtemisTableModule,
        // programming exercise sub modules.
        ArtemisProgrammingExerciseActionsModule,
    ],
    declarations: [ProgrammingExerciseManageTestCasesComponent, ProgrammingExerciseManageTestCasesStatusComponent, ProgrammingExerciseManageTestCasesActionsComponent],
    exports: [ProgrammingExerciseManageTestCasesComponent],
})
export class ArtemisProgrammingExerciseTestCaseModule {}
