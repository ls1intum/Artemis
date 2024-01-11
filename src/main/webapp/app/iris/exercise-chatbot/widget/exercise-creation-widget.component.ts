import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { AfterViewInit, Component, Input } from '@angular/core';
import { IrisStateStore } from 'app/iris/state-store.service';
import { SharedService } from 'app/iris/shared.service';
import { IrisSessionService } from 'app/iris/session.service';
import { UserService } from 'app/core/user/user.service';
import { TranslateService } from '@ngx-translate/core';
import { ChatbotService } from 'app/iris/exercise-chatbot/widget/chatbot.service';
import { IrisChatbotWidgetComponent } from 'app/iris/exercise-chatbot/widget/chatbot-widget.component';
import { MatDialog } from '@angular/material/dialog';
import interact from 'interactjs';

@Component({
    selector: 'jhi-exercise-creation-widget',
    templateUrl: 'exercise-creation-widget.component.html',
    styleUrl: 'chatbot-widget.component.scss',
})
export class ExerciseCreationWidgetComponent extends IrisChatbotWidgetComponent implements AfterViewInit {
    @Input()
    exerciseId: number;
    @Input()
    stateStore: IrisStateStore;
    @Input()
    courseId: number;
    @Input()
    sessionService: IrisSessionService;

    private resizableMaxWidthRight: number;

    constructor(
        dialog: MatDialog,
        userService: UserService,
        sharedService: SharedService,
        modalService: NgbModal,
        translateService: TranslateService,
        private chatbotService: ChatbotService,
    ) {
        super(dialog, userService, sharedService, modalService, translateService, document);
        this.chatbotService.displayChatObservable.subscribe();
    }

    ngAfterViewInit() {
        super.ngAfterViewInit();
        this.resizableMaxWidthRight = window.screen.width / 2;
        interact('.chat-widget')
            .resizable({
                edges: { left: true, right: false, bottom: true, top: false },
                modifiers: [
                    // keep the edges inside the parent
                    interact.modifiers.restrictSize({
                        min: { width: this.initialWidth, height: this.initialHeight },
                        max: { width: this.resizableMaxWidthRight, height: 2000 },
                    }),
                ],

                inertia: true,
            })
            .on('resizemove', function (event: any) {
                const target = event.target;
                // Update element height
                target.style.width = event.rect.width + 'px';
                target.style.height = event.rect.height + 'px';
            });
    }
}
