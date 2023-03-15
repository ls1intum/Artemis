import { NgModule } from '@angular/core';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';

import { ExerciseScoresComponent } from './exercise-scores.component';
import { ArtemisProgrammingAssessmentModule } from 'app/exercises/programming/assess/programming-assessment.module';
import { ExerciseScoresExportButtonComponent } from 'app/exercises/shared/exercise-scores/exercise-scores-export-button.component';
import { ArtemisExerciseScoresRoutingModule } from 'app/exercises/shared/exercise-scores/exercise-scores-routing.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { SubmissionExportButtonComponent } from 'app/exercises/shared/submission-export/submission-export-button.component';
import { SubmissionExportDialogComponent } from 'app/exercises/shared/submission-export/submission-export-dialog.component';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

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
    ],
    declarations: [ExerciseScoresComponent, SubmissionExportButtonComponent, SubmissionExportDialogComponent, ExerciseScoresExportButtonComponent],
    exports: [SubmissionExportButtonComponent, ExerciseScoresExportButtonComponent],
})
export class ArtemisExerciseScoresModule {}
