import { NgModule } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';

import { AssessmentInstructionsModule } from 'app/assessment/assessment-instructions/assessment-instructions.module';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { ProgrammingExerciseSubmissionsComponent } from 'app/exercises/programming/assess/programming-assessment-dashboard/programming-exercise-submissions.component';
import { ArtemisAssessmentProgressLabelModule } from 'app/exercises/shared/assessment-progress-label/assessment-progress-label.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [
        ArtemisSharedModule,
        FormDateTimePickerModule,
        FormsModule,
        FeatureToggleModule,
        AssessmentInstructionsModule,
        RouterModule.forChild([]),
        ArtemisResultModule,
        ArtemisAssessmentSharedModule,
        SubmissionResultStatusModule,
        ArtemisAssessmentProgressLabelModule,
    ],
    declarations: [ProgrammingExerciseSubmissionsComponent],
})
export class ArtemisProgrammingAssessmentDashboardModule {}
