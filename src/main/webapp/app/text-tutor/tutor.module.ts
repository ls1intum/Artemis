import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';

import { ArTEMiSTextTutorRoutingModule } from './tutor-routing.module';
import { ArTEMiSTextTutorComponent } from './tutor.component';
import { TextSelectDirective } from './text-assessment-editor/text-select.directive';
import { TextAssessmentEditorComponent } from './text-assessment-editor/text-assessment-editor.component';
import { ArTEMiSSharedModule } from 'app/shared';
import { ArTEMiSResultModule } from 'app/entities/result';
import { TextAssessmentDetailComponent } from './text-assessment-detail/text-assessment-detail.component';
import { TextAssessmentDashboardComponent } from './text-assessment-dashboard/text-assessment-dashboard.component';
import { SortByModule } from 'app/components/pipes';

@NgModule({
    declarations: [
        ArTEMiSTextTutorComponent,
        TextSelectDirective,
        TextAssessmentEditorComponent,
        TextAssessmentDetailComponent,
        TextAssessmentDashboardComponent,
    ],
    imports: [
        CommonModule,
        SortByModule,
        ArTEMiSTextTutorRoutingModule,
        ArTEMiSSharedModule,
        ArTEMiSResultModule,
    ]
})
export class ArTEMiSTextTutorModule {
}
