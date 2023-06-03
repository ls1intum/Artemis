import { NgModule } from '@angular/core';
import { MatDialogModule } from '@angular/material/dialog';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ExerciseChatWidgetComponent } from 'app/overview/exercise-chatbot/exercise-chatwidget/exercise-chat-widget.component';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { AngularDraggableModule } from 'angular2-draggable';
import { RouterModule } from '@angular/router';

@NgModule({
    declarations: [ExerciseChatWidgetComponent],
    imports: [MatDialogModule, FormsModule, CommonModule, FontAwesomeModule, ArtemisSharedModule, AngularDraggableModule, RouterModule],
    providers: [],
})
export class ExerciseChatbotModule {}
