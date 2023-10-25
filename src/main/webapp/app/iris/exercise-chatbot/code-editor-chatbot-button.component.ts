import { Component } from '@angular/core';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { Overlay } from '@angular/cdk/overlay';
import { IrisStateStore } from 'app/iris/state-store.service';
import { ActivatedRoute } from '@angular/router';
import { SharedService } from 'app/iris/shared.service';
import { IrisChatbotButtonComponent } from 'app/iris/exercise-chatbot/chatbot-button.component';
import { IrisCodeEditorSessionService } from 'app/iris/code-editor-session.service';
import { IrisCodeEditorWebsocketService } from 'app/iris/code-editor-websocket.service';
import { IrisLogoSize } from '../iris-logo/iris-logo.component';
import { IrisCodeEditorChatbotWidgetComponent } from 'app/iris/exercise-chatbot/widget/code-editor-chatbot-widget.component';

@Component({
    selector: 'jhi-code-editor-chatbot',
    templateUrl: './code-editor-chatbot-button.component.html',
    styleUrls: ['./code-editor-chatbot-button.component.scss'],
    providers: [IrisCodeEditorSessionService],
})
export class IrisCodeEditorChatbotButtonComponent extends IrisChatbotButtonComponent<IrisCodeEditorChatbotWidgetComponent> {
    protected readonly IrisLogoSize = IrisLogoSize;

    constructor(
        dialog: MatDialog,
        overlay: Overlay,
        codeEditorSessionService: IrisCodeEditorSessionService,
        stateStore: IrisStateStore,
        // Note: This unused service is injected to ensure that it is instantiated
        websocketService: IrisCodeEditorWebsocketService,
        route: ActivatedRoute,
        sharedService: SharedService,
    ) {
        super(dialog, overlay, codeEditorSessionService, stateStore, route, sharedService);
    }

    protected openDialog(): MatDialogRef<IrisCodeEditorChatbotWidgetComponent> {
        return this.dialog.open(IrisCodeEditorChatbotWidgetComponent, {
            hasBackdrop: false,
            scrollStrategy: this.overlay.scrollStrategies.noop(),
            position: { bottom: '0px', right: '0px' },
            disableClose: true,
            data: {
                stateStore: this.stateStore,
                courseId: this.courseId,
                exerciseId: this.exerciseId,
                sessionService: this.sessionService,
            },
        });
    }
}
