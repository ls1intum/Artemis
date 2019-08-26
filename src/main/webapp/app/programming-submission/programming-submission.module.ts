import { NgModule } from '@angular/core';

import { ArtemisSharedModule } from 'app/shared';
import { ProgrammingSubmissionService } from 'app/programming-submission/programming-submission.service';

@NgModule({
    imports: [ArtemisSharedModule],
    providers: [ProgrammingSubmissionService],
})
export class ArtemisProgrammingSubmissionModule {}
