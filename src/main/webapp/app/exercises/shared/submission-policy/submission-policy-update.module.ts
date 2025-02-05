import { NgModule } from '@angular/core';

import { SubmissionPolicyUpdateComponent } from 'app/exercises/shared/submission-policy/submission-policy-update.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

@NgModule({
    imports: [ArtemisSharedComponentModule, SubmissionPolicyUpdateComponent],
    exports: [SubmissionPolicyUpdateComponent],
})
export class SubmissionPolicyUpdateModule {}
