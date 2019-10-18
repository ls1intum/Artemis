import { NgModule } from '@angular/core';
import {
    ProgrammingAssessmentManualResultButtonComponent,
    ProgrammingAssessmentManualResultDialogComponent,
    ProgrammingAssessmentManualResultService,
} from 'app/programming-assessment/manual-result';
import { ProgrammingAssessmentRepoExportButtonComponent, ProgrammingAssessmentRepoExportDialogComponent } from 'app/programming-assessment/repo-export';

@NgModule({
    imports: [],
    declarations: [
        ProgrammingAssessmentManualResultButtonComponent,
        ProgrammingAssessmentManualResultDialogComponent,
        ProgrammingAssessmentRepoExportButtonComponent,
        ProgrammingAssessmentRepoExportDialogComponent,
    ],
    providers: [ProgrammingAssessmentManualResultService],
    exports: [ProgrammingAssessmentManualResultButtonComponent, ProgrammingAssessmentRepoExportButtonComponent],
})
export class ArtemisProgrammingAssessmentModule {}
