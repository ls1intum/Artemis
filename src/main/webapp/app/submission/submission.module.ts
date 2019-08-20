import { NgModule } from '@angular/core';

import { ArtemisSharedModule } from 'app/shared';
import { ProgrammingSubmissionWebsocketService } from 'app/submission/programming-submission-websocket.service';

@NgModule({
    imports: [ArtemisSharedModule],
    providers: [ProgrammingSubmissionWebsocketService],
})
export class ArtemisProgrammingSubmissionModule {}
