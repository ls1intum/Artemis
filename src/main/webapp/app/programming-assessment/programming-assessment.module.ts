import { NgModule } from '@angular/core';
import {
    ProgrammingAssessmentManualResultButtonComponent,
    ProgrammingAssessmentManualResultComponent,
    ProgrammingAssessmentManualResultService,
} from 'app/programming-assessment/manual-result';
import { ProgrammingAssessmentRepoExportButtonComponent, ProgrammingAssessmentRepoExportDialogComponent } from 'app/programming-assessment/repo-export';
import { ArtemisSharedModule } from 'app/shared';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { FormsModule } from '@angular/forms';
import { ArtemisComplaintsForTutorModule } from 'app/complaints-for-tutor';
import { programmingAssessmentRoutes } from 'app/programming-assessment/programming-assessment.route';
import { RouterModule } from '@angular/router';

const ENTITY_STATES = [...programmingAssessmentRoutes];

@NgModule({
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, FormDateTimePickerModule, FormsModule, ArtemisComplaintsForTutorModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [
        ProgrammingAssessmentManualResultButtonComponent,
        ProgrammingAssessmentManualResultComponent,
        ProgrammingAssessmentRepoExportButtonComponent,
        ProgrammingAssessmentRepoExportDialogComponent,
    ],
    entryComponents: [ProgrammingAssessmentManualResultComponent, ProgrammingAssessmentRepoExportDialogComponent],
    providers: [ProgrammingAssessmentManualResultService],
    exports: [ProgrammingAssessmentManualResultButtonComponent, ProgrammingAssessmentRepoExportButtonComponent],
})
export class ArtemisProgrammingAssessmentModule {}
