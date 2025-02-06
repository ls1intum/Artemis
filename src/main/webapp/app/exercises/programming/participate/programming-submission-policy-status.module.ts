import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';

import { ProgrammingSubmissionPolicyStatusComponent } from 'app/exercises/programming/participate/programming-submission-policy-status';

@NgModule({
    imports: [CommonModule, ProgrammingSubmissionPolicyStatusComponent],
    exports: [ProgrammingSubmissionPolicyStatusComponent],
})
export class ArtemisProgrammingSubmissionPolicyStatusModule {}
