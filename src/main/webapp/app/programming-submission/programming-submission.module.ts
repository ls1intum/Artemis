import { NgModule } from '@angular/core';

import { ArtemisSharedModule } from 'app/shared';
import { ProgrammingSubmissionWebsocketService } from 'app/programming-submission/programming-submission-websocket.service';

@NgModule({
    imports: [ArtemisSharedModule],
})
export class ArtemisProgrammingSubmissionModule {
    static forRoot() {
        return {
            ngModule: ArtemisProgrammingSubmissionModule,
            providers: [ProgrammingSubmissionWebsocketService],
        };
    }
}
