import { NgModule } from '@angular/core';
import { ChatbotPopupComponent } from './chatbot-popup/chatbot-popup.component';
import { MatDialogModule } from '@angular/material/dialog';

@NgModule({
    declarations: [ChatbotPopupComponent],
    imports: [MatDialogModule],
    providers: [],
})
export class ExerciseChatbotModule {}
