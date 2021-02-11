import { NgModule } from '@angular/core';
import { ProgrammingAssessmentDashboardComponent } from 'app/exercises/programming/assess/programming-assessment-dashboard/programming-assessment-dashboard.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { AssessmentInstructionsModule } from 'app/assessment/assessment-instructions/assessment-instructions.module';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';

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
    ],
    declarations: [ProgrammingAssessmentDashboardComponent],
})
export class ArtemisProgrammingAssessmentDashboardModule {}
