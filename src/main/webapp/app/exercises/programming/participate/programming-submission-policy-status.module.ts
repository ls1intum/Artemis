import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ProgrammingSubmissionPolicyStatusComponent } from 'app/exercises/programming/participate/programming-submission-policy-status';

@NgModule({
    declarations: [ProgrammingSubmissionPolicyStatusComponent],
    imports: [CommonModule, ArtemisSharedCommonModule],
    exports: [ProgrammingSubmissionPolicyStatusComponent],
})
export class ArtemisProgrammingSubmissionPolicyStatusModule {}
