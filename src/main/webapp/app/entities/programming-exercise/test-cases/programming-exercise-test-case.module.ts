import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import {
    ProgrammingExerciseManageTestCasesActionsComponent,
    ProgrammingExerciseManageTestCasesComponent,
    ProgrammingExerciseManageTestCasesStatusComponent,
    ProgrammingExerciseTestCasesDirtyWarningComponent,
} from './';
import { ArtemisTableModule } from 'app/components/table/table.module';
import { ArtemisSharedModule } from 'app/shared';
import { ArtemisProgrammingExerciseActionsModule } from 'app/entities/programming-exercise/actions/programming-exercise-actions.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

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
