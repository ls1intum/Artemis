import { IrisMessageStore } from 'app/iris/message-store.service';
import { IrisWebsocketService } from 'app/iris/websocket.service';
import { IrisSessionService } from 'app/iris/session.service';
import { NgModule } from '@angular/core';
import { MatDialogModule } from '@angular/material/dialog';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms'; // Import FormsModule
import { ExerciseChatWidgetComponent } from 'app/iris/exercise-chatbot/exercise-chatwidget/exercise-chat-widget.component';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ChatbotPopupComponent } from 'app/iris/exercise-chatbot/chatbot-popup/chatbot-popup.component';
import { ExerciseChatbotComponent } from 'app/iris/exercise-chatbot/exercise-chatbot.component';

@NgModule({
    declarations: [ChatbotPopupComponent, ExerciseChatWidgetComponent, ExerciseChatbotComponent],
    imports: [CommonModule, MatDialogModule, FormsModule, FontAwesomeModule, ArtemisSharedModule],
    providers: [IrisMessageStore, IrisWebsocketService, IrisSessionService],
})
export class IrisModule {}
