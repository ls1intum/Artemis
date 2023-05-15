import { NgModule } from '@angular/core';
import { ChatbotPopupComponent } from './chatbot-popup/chatbot-popup.component';
import { MatDialogModule } from '@angular/material/dialog';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms'; // Import FormsModule
import { ExerciseChatWidgetComponent } from 'app/overview/exercise-chatbot/exercise-chatwidget/exercise-chat-widget.component';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';

@NgModule({
    declarations: [ChatbotPopupComponent, ExerciseChatWidgetComponent],
    imports: [MatDialogModule, FormsModule, CommonModule, FontAwesomeModule],
    providers: [],
})
export class ExerciseChatbotModule {}
