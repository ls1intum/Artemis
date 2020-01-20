import { ModelingSubmissionComponent } from './modeling-submission.component';
import { RouterModule } from '@angular/router';
import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from '../shared';
import { modelingSubmissionRoute } from './modeling-submission.route';
import { ArtemisResultModule } from '../entities/result';
import { AceEditorModule } from 'ng2-ace-editor';
import { ArtemisModelingEditorModule } from 'app/modeling-editor';
import { ModelingAssessmentModule } from 'app/modeling-assessment/modeling-assessment.module';
import { ArtemisComplaintsModule } from 'app/complaints';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { MomentModule } from 'ngx-moment';

const ENTITY_STATES = [...modelingSubmissionRoute];

// TODO CZ: do we need all these imports?
@NgModule({
    imports: [
        ArtemisSharedModule,
        RouterModule.forChild(ENTITY_STATES),
        ArtemisResultModule,
        AceEditorModule,
        ArtemisModelingEditorModule,
        ModelingAssessmentModule,
        ArtemisComplaintsModule,
        ArtemisSharedComponentModule,
        MomentModule,
    ],
    declarations: [ModelingSubmissionComponent],
})
export class ArtemisModelingSubmissionModule {}
