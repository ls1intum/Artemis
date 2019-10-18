import { NgModule } from '@angular/core';
import {
    ProgrammingAssessmentManualResultButtonComponent,
    ProgrammingAssessmentManualResultDialogComponent,
    ProgrammingAssessmentManualResultService,
} from 'app/programming-assessment/manual-result';
import { ProgrammingAssessmentRepoExportButtonComponent, ProgrammingAssessmentRepoExportDialogComponent } from 'app/programming-assessment/repo-export';
import { ArtemisSharedModule } from 'app/shared';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { FormsModule } from '@angular/forms';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, FormDateTimePickerModule, FormsModule],
    declarations: [
        ProgrammingAssessmentManualResultButtonComponent,
        ProgrammingAssessmentManualResultDialogComponent,
        ProgrammingAssessmentRepoExportButtonComponent,
        ProgrammingAssessmentRepoExportDialogComponent,
    ],
    entryComponents: [ProgrammingAssessmentManualResultDialogComponent, ProgrammingAssessmentRepoExportDialogComponent],
    providers: [ProgrammingAssessmentManualResultService],
    exports: [ProgrammingAssessmentManualResultButtonComponent, ProgrammingAssessmentRepoExportButtonComponent],
})
export class ArtemisProgrammingAssessmentModule {}
