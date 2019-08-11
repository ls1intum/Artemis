import { NgModule } from '@angular/core';

import { ArTEMiSSharedModule } from 'app/shared';
import { ProgrammingSubmissionWebsocketService } from 'app/submission/programming-submission-websocket.service';

@NgModule({
    imports: [ArTEMiSSharedModule],
    providers: [ProgrammingSubmissionWebsocketService],
})
export class ArTEMiSProgrammingSubmissionModule {}
