import { NgModule } from '@angular/core';

import { ArtemisSharedModule } from 'app/shared';
import { ProgrammingSubmissionService } from 'app/programming-submission/programming-submission.service';

@NgModule({
    imports: [ArtemisSharedModule],
})
export class ArtemisProgrammingSubmissionModule {
    static forRoot() {
        return {
            ngModule: ArtemisProgrammingSubmissionModule,
            providers: [ProgrammingSubmissionService],
        };
    }
}
