import { ModelingSubmissionComponent } from './modeling-submission.component';
import { RouterModule } from '@angular/router';
import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { modelingSubmissionRoute } from './modeling-submission.route';
import { AceEditorModule } from 'ng2-ace-editor';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { MomentModule } from 'ngx-moment';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisComplaintsModule } from 'app/complaints/complaints.module';
import { ArtemisModelingEditorModule } from 'app/exercises/modeling/shared/modeling-editor/modeling-editor.module';
import { ModelingAssessmentModule } from 'app/exercises/modeling/assess/modeling-assessment/modeling-assessment.module';

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
