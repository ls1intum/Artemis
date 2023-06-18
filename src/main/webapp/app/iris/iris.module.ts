import { NgModule } from '@angular/core';
import { MatDialogModule } from '@angular/material/dialog';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms'; // Import FormsModule
import { ExerciseChatWidgetComponent } from 'app/iris/exercise-chatbot/exercise-chatwidget/exercise-chat-widget.component';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ExerciseChatbotComponent } from 'app/iris/exercise-chatbot/exercise-chatbot.component';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { AboutIrisComponent } from 'app/iris/about-iris/about-iris.component';
import { AngularDraggableModule } from 'angular2-draggable';
import { RouterModule } from '@angular/router';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

@NgModule({
    declarations: [ExerciseChatWidgetComponent, ExerciseChatbotComponent, AboutIrisComponent],
    imports: [
        CommonModule,
        MatDialogModule,
        FormsModule,
        ArtemisSharedComponentModule,
        FontAwesomeModule,
        ArtemisSharedModule,
        AngularDraggableModule,
        ArtemisMarkdownModule,
        RouterModule,
    ],
    providers: [],
    exports: [ExerciseChatbotComponent],
})
export class IrisModule {}
