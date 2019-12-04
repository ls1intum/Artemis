import { NgModule } from '@angular/core';
import {
    ProgrammingAssessmentManualResultButtonComponent,
    ProgrammingAssessmentManualResultDialogComponent,
    ProgrammingAssessmentManualResultService,
} from 'app/programming-assessment/manual-result';
import { ProgrammingAssessmentRepoExportButtonComponent, ProgrammingAssessmentRepoExportDialogComponent } from 'app/programming-assessment/repo-export';
import { ArtemisSharedModule } from 'app/shared';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { FormsModule } from '@angular/forms';
import { BuildLogService } from 'app/programming-assessment/build-logs/build-log.service';
import { FeatureToggleModule } from 'app/feature-toggle/feature-toggle.module';
import { ArtemisComplaintsForTutorModule } from 'app/complaints-for-tutor';

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
