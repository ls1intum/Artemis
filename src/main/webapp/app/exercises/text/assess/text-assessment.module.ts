import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { textAssessmentRoutes } from './text-assessment.route';
import { TextAssessmentEditorComponent } from './text-assessment-editor/text-assessment-editor.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { TextAssessmentDashboardComponent } from './text-assessment-dashboard/text-assessment-dashboard.component';
import { RouterModule } from '@angular/router';
import { ResizableInstructionsComponent } from 'app/exercises/text/assess/resizable-instructions/resizable-instructions.component';
import { HighlightedTextAreaComponent } from 'app/exercises/text/assess/highlighted-text-area/highlighted-text-area.component';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { ArtemisComplaintsForTutorModule } from 'app/complaints/complaints-for-tutor/complaints-for-tutor.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { AssessmentInstructionsModule } from 'app/assessment/assessment-instructions/assessment-instructions.module';
import { TextSharedModule } from 'app/exercises/text/shared/text-shared.module';
import { TextSubmissionExportButtonComponent } from 'app/exercises/text/assess/submission-export/text-submission-export-button.component';
import { TextSubmissionExportDialogComponent } from 'app/exercises/text/assess/submission-export/text-submission-export-dialog.component';

const ENTITY_STATES = [...textAssessmentRoutes];
@NgModule({
    imports: [
        CommonModule,
        RouterModule.forChild(ENTITY_STATES),
        ArtemisSharedModule,
        ArtemisResultModule,
        ArtemisComplaintsForTutorModule,
        ArtemisAssessmentSharedModule,
        AssessmentInstructionsModule,
        TextSharedModule,
    ],
    declarations: [
        TextAssessmentEditorComponent,
        TextAssessmentDashboardComponent,
        ResizableInstructionsComponent,
        HighlightedTextAreaComponent,
        TextSubmissionExportButtonComponent,
        TextSubmissionExportDialogComponent,
    ],
    exports: [TextAssessmentEditorComponent, ResizableInstructionsComponent, TextSubmissionExportButtonComponent],
})
export class ArtemisTextExerciseAssessmentModule {}
