import { NgModule } from '@angular/core';

import { SubmissionPolicyUpdateComponent } from 'app/exercises/shared/submission-policy/submission-policy-update.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule],
    declarations: [SubmissionPolicyUpdateComponent],
    exports: [SubmissionPolicyUpdateComponent],
})
export class SubmissionPolicyUpdateModule {}
