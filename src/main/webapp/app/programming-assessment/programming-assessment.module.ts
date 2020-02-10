import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { FormsModule } from '@angular/forms';
import { BuildLogService } from 'app/programming-assessment/build-logs/build-log.service';
import { FeatureToggleModule } from 'app/feature-toggle/feature-toggle.module';
import { ProgrammingAssessmentManualResultButtonComponent } from 'app/programming-assessment/manual-result/programming-assessment-manual-result-button.component';
import { ProgrammingAssessmentRepoExportButtonComponent } from 'app/programming-assessment/repo-export/programming-assessment-repo-export-button.component';
import { ProgrammingAssessmentManualResultDialogComponent } from 'app/programming-assessment/manual-result/programming-assessment-manual-result-dialog.component';
import { ArtemisComplaintsForTutorModule } from 'app/complaints-for-tutor/complaints-for-tutor.module';
import { ProgrammingAssessmentManualResultService } from 'app/programming-assessment/manual-result/programming-assessment-manual-result.service';
import { ProgrammingAssessmentRepoExportDialogComponent } from 'app/programming-assessment/repo-export/programming-assessment-repo-export-dialog.component';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, FormDateTimePickerModule, FormsModule, FeatureToggleModule, ArtemisComplaintsForTutorModule],
    declarations: [
        ProgrammingAssessmentManualResultButtonComponent,
        ProgrammingAssessmentManualResultDialogComponent,
        ProgrammingAssessmentRepoExportButtonComponent,
        ProgrammingAssessmentRepoExportDialogComponent,
    ],
    entryComponents: [ProgrammingAssessmentManualResultDialogComponent, ProgrammingAssessmentRepoExportDialogComponent],
    providers: [ProgrammingAssessmentManualResultService, BuildLogService],
    exports: [ProgrammingAssessmentManualResultButtonComponent, ProgrammingAssessmentRepoExportButtonComponent],
})
export class ArtemisProgrammingAssessmentModule {}
