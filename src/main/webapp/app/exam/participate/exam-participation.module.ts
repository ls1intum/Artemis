import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ExamParticipationComponent } from 'app/exam/participate/exam-participation.component';
import { ExamParticipationCoverComponent } from './exam-cover/exam-participation-cover.component';
import { ExamParticipationSummaryComponent } from 'app/exam/participate/summary/exam-participation-summary.component';
import { examParticipationState } from 'app/exam/participate/exam-participation.route';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { TextEditorExamComponent } from 'app/exam/participate/exercises/text/text-editor-exam.component';
import { ModelingSubmissionExamComponent } from 'app/exam/participate/exercises/modeling/modeling-submission-exam.component';
import { ArtemisModelingEditorModule } from 'app/exercises/modeling/shared/modeling-editor.module';
import { ArtemisFullscreenModule } from 'app/shared/fullscreen/fullscreen.module';

const ENTITY_STATES = [...examParticipationState];

@NgModule({
    imports: [RouterModule.forChild(ENTITY_STATES), ArtemisSharedCommonModule, ArtemisSharedModule, ArtemisModelingEditorModule, ArtemisFullscreenModule],
    declarations: [ExamParticipationComponent, ExamParticipationCoverComponent, ExamParticipationSummaryComponent, TextEditorExamComponent, ModelingSubmissionExamComponent],
})
export class ArtemisExamParticipationModule {}
