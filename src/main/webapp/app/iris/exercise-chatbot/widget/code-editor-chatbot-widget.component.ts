import { IrisChatbotWidgetComponent } from 'app/iris/exercise-chatbot/widget/chatbot-widget.component';
import { IrisMessage, IrisSender, IrisUserMessage } from 'app/entities/iris/iris-message.model';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { IrisMessageContent, getPlanComponent, isPlanContent } from 'app/entities/iris/iris-content-type.model';
import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialog } from '@angular/material/dialog';
import { UserService } from 'app/core/user/user.service';
import { SharedService } from 'app/iris/shared.service';
import { DOCUMENT } from '@angular/common';
import { TranslateService } from '@ngx-translate/core';
import { Router } from '@angular/router';
import { animate, state, style, transition, trigger } from '@angular/animations';
import { IrisCodeEditorSessionService } from 'app/iris/code-editor-session.service';
import { UnsavedChangesDTO } from 'app/iris/http-code-editor-message.service';
import { ConversationErrorOccurredAction } from 'app/iris/state-store.model';
import { IrisErrorMessageKey } from 'app/entities/iris/iris-errors.model';
import { IrisExercisePlanStep } from 'app/entities/iris/iris-exercise-plan-component.model';

@Component({
    selector: 'jhi-code-editor-chatbot-widget',
    templateUrl: './code-editor-chatbot-widget.component.html',
    styleUrls: ['./code-editor-chatbot-widget.component.scss'],
    animations: [
        trigger('fadeAnimation', [
            state(
                'start',
                style({
                    opacity: 1,
                }),
            ),
            state(
                'end',
                style({
                    opacity: 0,
                }),
            ),
            transition('start => end', [animate('2s ease')]),
        ]),
    ],
})
export class IrisCodeEditorChatbotWidgetComponent extends IrisChatbotWidgetComponent {
    protected readonly IrisSender = IrisSender;

    private codeEditorSessionService: IrisCodeEditorSessionService;

    constructor(
        dialog: MatDialog,
        @Inject(MAT_DIALOG_DATA) data: any,
        userService: UserService,
        router: Router,
        sharedService: SharedService,
        modalService: NgbModal,
        @Inject(DOCUMENT) document: Document,
        translateService: TranslateService,
    ) {
        super(dialog, data, userService, router, sharedService, modalService, document, translateService);
        this.codeEditorSessionService = data.sessionService as IrisCodeEditorSessionService;
    }

    protected getFirstMessageContent(): string {
        this.importExerciseUrl = `/course-management/${this.courseId}/programming-exercises/import/${this.exerciseId}`;
        return this.translateService
            .instant('artemisApp.exerciseChatbot.codeEditorFirstMessage')
            .replace(/{link:(.*)}/, '<a href="' + this.importExerciseUrl + '" target="_blank">$1</a>');
    }

    protected sendCreateRequest(message: IrisUserMessage): Promise<IrisMessage> {
        const unsavedChanges = new UnsavedChangesDTO(); // TODO: get unsaved changes from editor
        return this.codeEditorSessionService.createMessage(this.sessionId, message, unsavedChanges);
    }

    protected sendResendRequest(message: IrisUserMessage): Promise<IrisMessage> {
        const unsavedChanges = new UnsavedChangesDTO(); // TODO: get unsaved changes from editor
        return this.codeEditorSessionService.resendMessage(this.sessionId, message, unsavedChanges);
    }

    /**
     * execute a step of an exercise plan
     * @param messageId of the message
     * @param planId of the exercise plan
     * @param stepId of the plan step
     */
    executeExercisePlanStep(messageId: number, planId: number, stepId: number) {
        const unsavedChanges = new UnsavedChangesDTO(); // TODO: get unsaved changes from editor
        // TODO: The then() and catch() methods should alert the user if the plan step was executed successfully or not
        // TODO: Implement messages for the user
        this.codeEditorSessionService
            .executePlanStep(this.sessionId, messageId, planId, stepId, unsavedChanges)
            .then(() => {}) // TODO: Notify the user that the step was executed successfully
            .catch(() => {
                this.stateStore.dispatch(new ConversationErrorOccurredAction(IrisErrorMessageKey.TECHNICAL_ERROR_RESPONSE));
                // TODO: Scroll to the step that failed to execute
            });
    }

    /**
     * update the instructions of an exercise plan step
     * @param messageId of the message
     * @param planId of the exercise plan
     * @param stepId of the plan step
     * @param step with the updated instructions
     */
    updateExercisePlanStep(messageId: number, planId: number, stepId: number, step: IrisExercisePlanStep) {
        this.codeEditorSessionService
            .updatePlanStepInstructions(this.sessionId, messageId, planId, stepId, step)
            .then(() => {}) // TODO: Notify the user that the step was updated successfully
            .catch(() => {
                this.stateStore.dispatch(new ConversationErrorOccurredAction(IrisErrorMessageKey.TECHNICAL_ERROR_RESPONSE));
                // TODO: Scroll to the step that failed to update
            });
    }

    isExercisePlan(content: IrisMessageContent) {
        return isPlanContent(content);
    }

    getExercisePlanSteps(content: IrisMessageContent) {
        return getPlanComponent(content);
    }
}
