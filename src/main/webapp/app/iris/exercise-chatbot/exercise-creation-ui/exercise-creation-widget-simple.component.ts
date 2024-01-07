import { ChatbotService } from 'app/iris/exercise-chatbot/exercise-creation-ui/chatbot.service';
import { Component, Input } from '@angular/core';
import { IrisStateStore } from 'app/iris/state-store.service';
import { IrisSessionService } from 'app/iris/session.service';

@Component({
    selector: 'jhi-exercise-creation-widget-simple',
    templateUrl: 'exercise-creation-widget-simple.component.html',
})
export class ExerciseCreationWidgetSimpleComponent {
    @Input()
    exerciseId: number;
    @Input()
    stateStore: IrisStateStore;
    @Input()
    courseId: number;
    @Input()
    sessionService: IrisSessionService;
    @Input()
    paramsOnSend: () => Record<string, unknown> = () => ({});

    chatOpen: boolean;

    constructor(
        private chatbotService: ChatbotService,
        // ... other dependencies
    ) {
        // ... existing code

        // Subscribe to the displayChat event
        this.chatbotService.displayChat$.subscribe(() => {
            this.displayChat();
        });
    }

    // Add a method to manually trigger the display of the chat
    displayChat() {
        // Add logic to display the chat in the widget
        this.chatOpen = true;
    }
}
