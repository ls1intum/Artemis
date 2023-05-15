import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IrisMessageStore } from 'app/iris/message-store.service';
import { IrisHttpMessageService } from 'app/iris/http-message.service';
import { IrisWebsocketService } from 'app/iris/websocket.service';
import { IrisSessionService } from 'app/iris/session.service';

@NgModule({
    declarations: [],
    imports: [CommonModule],
    providers: [IrisMessageStore, IrisHttpMessageService, IrisWebsocketService, IrisSessionService],
})
export class IrisModule {}
