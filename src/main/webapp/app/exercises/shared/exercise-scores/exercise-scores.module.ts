import { NgModule } from '@angular/core';
import { ManageAssessmentButtonsComponent } from 'app/exercises/shared/exercise-scores/manage-assessment-buttons.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ExerciseScoresComponent } from './exercise-scores.component';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisProgrammingAssessmentModule } from 'app/exercises/programming/assess/programming-assessment.module';
import { NgxDatatableModule } from '@siemens/ngx-datatable';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisExerciseScoresRoutingModule } from 'app/exercises/shared/exercise-scores/exercise-scores-routing.module';
import { SubmissionExportButtonComponent } from 'app/exercises/shared/submission-export/submission-export-button.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { SubmissionExportDialogComponent } from 'app/exercises/shared/submission-export/submission-export-dialog.component';
import { ExerciseScoresExportButtonComponent } from 'app/exercises/shared/exercise-scores/exercise-scores-export-button.component';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { ExternalSubmissionDialogComponent } from 'app/exercises/shared/external-submission/external-submission-dialog.component';
import { ExternalSubmissionButtonComponent } from 'app/exercises/shared/external-submission/external-submission-button.component';

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisExerciseScoresRoutingModule,
        NgbModule,
        ArtemisResultModule,
        FormDateTimePickerModule,
        NgxDatatableModule,
        ArtemisDataTableModule,
        ArtemisProgrammingAssessmentModule,
        FeatureToggleModule,
        ArtemisSharedComponentModule,
        SubmissionResultStatusModule,
        ArtemisMarkdownModule,
    ],
    declarations: [
        ExerciseScoresComponent,
        SubmissionExportButtonComponent,
        SubmissionExportDialogComponent,
        ExerciseScoresExportButtonComponent,
        ExternalSubmissionButtonComponent,
        ExternalSubmissionDialogComponent,
        ManageAssessmentButtonsComponent,
    ],
    exports: [SubmissionExportButtonComponent, ExerciseScoresExportButtonComponent, ExternalSubmissionButtonComponent],
})
export class ArtemisExerciseScoresModule {}
