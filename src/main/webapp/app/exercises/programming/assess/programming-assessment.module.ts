import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { FormsModule } from '@angular/forms';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { ProgrammingAssessmentManualResultButtonComponent } from 'app/exercises/programming/assess/manual-result/programming-assessment-manual-result-button.component';
import { ProgrammingAssessmentRepoExportButtonComponent } from 'app/exercises/programming/assess/repo-export/programming-assessment-repo-export-button.component';
import { ArtemisComplaintsForTutorModule } from 'app/complaints/complaints-for-tutor/complaints-for-tutor.module';
import { ProgrammingAssessmentRepoExportDialogComponent } from 'app/exercises/programming/assess/repo-export/programming-assessment-repo-export-dialog.component';
import { ArtemisProgrammingAssessmentRoutingModule } from 'app/exercises/programming/assess/programming-assessment.route';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { ArtemisCodeEditorModule } from 'app/exercises/programming/shared/code-editor/code-editor.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisExerciseHintParticipationModule } from 'app/exercises/shared/exercise-hint/participate/exercise-hint-participation.module';
import { ArtemisProgrammingExerciseInstructionsRenderModule } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instructions-render.module';
import { ProgrammingAssessmentManualResultInCodeEditorComponent } from 'app/exercises/programming/assess/manual-result/programming-assessment-manual-result-in-code-editor.component';
import { CodeEditorTutorAssessmentContainerComponent } from 'app/exercises/programming/assess/code-editor-tutor-assessment-container.component';

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisSharedComponentModule,
        FormDateTimePickerModule,
        FormsModule,
        FeatureToggleModule,
        ArtemisComplaintsForTutorModule,
        ArtemisProgrammingAssessmentRoutingModule,
        ArtemisAssessmentSharedModule,
        ArtemisCodeEditorModule,
        ArtemisResultModule,
        ArtemisExerciseHintParticipationModule,
        ArtemisProgrammingExerciseInstructionsRenderModule,
    ],
    declarations: [
        ProgrammingAssessmentManualResultButtonComponent,
        ProgrammingAssessmentRepoExportButtonComponent,
        ProgrammingAssessmentRepoExportDialogComponent,
        ProgrammingAssessmentManualResultInCodeEditorComponent,
        CodeEditorTutorAssessmentContainerComponent,
    ],
    entryComponents: [ProgrammingAssessmentRepoExportDialogComponent],
    exports: [ProgrammingAssessmentManualResultButtonComponent, ProgrammingAssessmentRepoExportButtonComponent],
})
export class ArtemisProgrammingAssessmentModule {}
