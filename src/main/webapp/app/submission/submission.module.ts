import { NgModule } from '@angular/core';

import { ArTEMiSSharedModule } from 'app/shared';
import { SubmissionWebsocketService } from 'app/submission/submission-websocket.service';

@NgModule({
    imports: [ArTEMiSSharedModule],
    providers: [SubmissionWebsocketService],
})
export class ArTEMiSSubmissionModule {}
