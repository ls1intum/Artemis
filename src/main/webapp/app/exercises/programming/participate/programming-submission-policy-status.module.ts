import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ProgrammingSubmissionPolicyStatusComponent } from 'app/exercises/programming/participate/programming-submission-policy-status';

@NgModule({
    imports: [CommonModule, ArtemisSharedCommonModule, ProgrammingSubmissionPolicyStatusComponent],
    exports: [ProgrammingSubmissionPolicyStatusComponent],
})
export class ArtemisProgrammingSubmissionPolicyStatusModule {}
