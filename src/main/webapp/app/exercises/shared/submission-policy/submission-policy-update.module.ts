import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { SubmissionPolicyUpdateComponent } from 'app/exercises/shared/submission-policy/submission-policy-update.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule],
    declarations: [SubmissionPolicyUpdateComponent],
    exports: [SubmissionPolicyUpdateComponent],
})
export class SubmissionPolicyUpdateModule {}
