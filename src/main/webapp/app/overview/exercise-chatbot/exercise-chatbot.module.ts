import { NgModule } from '@angular/core';
import { ChatbotPopupComponent } from './chatbot-popup/chatbot-popup.component';
import { MatDialogModule } from '@angular/material/dialog';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms'; // Import FormsModule
import { ExerciseChatwidgetComponent } from 'app/overview/exercise-chatbot/exercise-chatwidget/exercise-chatwidget.component';

@NgModule({
    declarations: [ChatbotPopupComponent, ExerciseChatwidgetComponent],
    imports: [MatDialogModule, FormsModule, CommonModule],
    providers: [],
})
export class ExerciseChatbotModule {}
