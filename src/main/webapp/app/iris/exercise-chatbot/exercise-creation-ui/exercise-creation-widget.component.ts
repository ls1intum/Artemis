import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Component, Input } from '@angular/core';
import { IrisStateStore } from 'app/iris/state-store.service';
import { SharedService } from 'app/iris/shared.service';
import { IrisSessionService } from 'app/iris/session.service';
import { UserService } from 'app/core/user/user.service';
import { TranslateService } from '@ngx-translate/core';
import { ChatbotService } from 'app/iris/exercise-chatbot/exercise-creation-ui/chatbot.service';
import { IrisChatbotWidgetBasicComponent } from 'app/iris/exercise-chatbot/exercise-creation-ui/chatbot-widget-basic.component';
import { MatDialog } from '@angular/material/dialog';

@Component({
    selector: 'jhi-exercise-creation-widget',
    templateUrl: 'exercise-creation-widget.component.html',
    styleUrl: '../widget/chatbot-widget.component.scss',
})
export class ExerciseCreationWidgetComponent extends IrisChatbotWidgetBasicComponent {
    @Input()
    exerciseId: number;
    @Input()
    stateStore: IrisStateStore;
    @Input()
    courseId: number;
    @Input()
    sessionService: IrisSessionService;

    constructor(
        dialog: MatDialog,
        userService: UserService,
        sharedService: SharedService,
        modalService: NgbModal,
        translateService: TranslateService,
        private chatbotService: ChatbotService,
    ) {
        super(dialog, userService, sharedService, modalService, translateService, document);
        this.chatbotService.displayChat$.subscribe();
    }
}
