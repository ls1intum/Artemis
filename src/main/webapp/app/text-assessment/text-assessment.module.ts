import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';

import { textAssessmentRoutes } from './text-assessment.route';
import { TextAssessmentComponent } from './text-assessment.component';
import { TextSelectDirective } from './text-assessment-editor/text-select.directive';
import { TextAssessmentEditorComponent } from './text-assessment-editor/text-assessment-editor.component';
import { ArtemisSharedModule } from 'app/shared';
import { ArtemisResultModule } from 'app/entities/result';
import { TextAssessmentDashboardComponent } from './text-assessment-dashboard/text-assessment-dashboard.component';
import { SortByModule } from 'app/components/pipes';
import { RouterModule } from '@angular/router';
import { ResizableInstructionsComponent } from 'app/text-assessment/resizable-instructions/resizable-instructions.component';
import { ArtemisComplaintsForTutorModule } from 'app/complaints-for-tutor';
import { HighlightedTextAreaComponent } from 'app/text-assessment/highlighted-text-area/highlighted-text-area.component';
import { ArtemisAssessmentSharedModule } from 'app/assessment-shared';

const ENTITY_STATES = [...textAssessmentRoutes];
@NgModule({
    imports: [
        CommonModule,
        SortByModule,
        RouterModule.forChild(ENTITY_STATES),
        ArtemisSharedModule,
        ArtemisResultModule,
        ArtemisComplaintsForTutorModule,
        ArtemisAssessmentSharedModule,
    ],
    declarations: [
        TextAssessmentComponent,
        TextSelectDirective,
        TextAssessmentEditorComponent,
        TextAssessmentDashboardComponent,
        ResizableInstructionsComponent,
        HighlightedTextAreaComponent,
    ],
    exports: [TextAssessmentEditorComponent, ResizableInstructionsComponent],
})
export class ArtemisTextAssessmentModule {}
