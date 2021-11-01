import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { SubmissionPolicyUpdateComponent } from 'app/exercises/shared/submission-policy/submission-policy-update.component';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [SubmissionPolicyUpdateComponent],
    exports: [SubmissionPolicyUpdateComponent],
})
export class SubmissionPolicyUpdateModule {}
