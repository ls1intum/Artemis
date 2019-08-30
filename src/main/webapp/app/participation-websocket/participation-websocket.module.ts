import { NgModule } from '@angular/core';

import { ArtemisSharedModule } from 'app/shared';
import { ParticipationWebsocketService } from 'app/participation-websocket/participation-websocket.service';

@NgModule({
    imports: [ArtemisSharedModule],
})
export class ArtemisParticipationWebsocketModule {
    static forRoot() {
        return {
            ngModule: ArtemisParticipationWebsocketModule,
            providers: [ParticipationWebsocketService],
        };
    }
}
