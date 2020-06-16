import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ExamParticipationComponent } from 'app/exam/participate/exam-participation.component';
import { ExamParticipationStartComponent } from 'app/exam/participate/start/exam-participation-start.component';
import { ExamParticipationEndComponent } from 'app/exam/participate/end/exam-participation-end.component';
import { ExamParticipationSummaryComponent } from 'app/exam/participate/summary/exam-participation-summary.component';
import { examParticipationState } from 'app/exam/participate/exam-participation.route';
import { ArtemisSharedModule } from 'app/shared/shared.module';

const ENTITY_STATES = [...examParticipationState];

@NgModule({
    imports: [RouterModule.forChild(ENTITY_STATES), ArtemisSharedCommonModule, ArtemisSharedModule],
    declarations: [ExamParticipationComponent, ExamParticipationStartComponent, ExamParticipationEndComponent, ExamParticipationSummaryComponent],
})
export class ArtemisExamParticipationModule {}
