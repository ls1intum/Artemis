import { NgModule } from '@angular/core';
import { MatDialogModule } from '@angular/material/dialog';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms'; // Import FormsModule
import { ExerciseChatWidgetComponent } from 'app/iris/exercise-chatbot/exercise-chatwidget/exercise-chat-widget.component';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ChatbotPopupComponent } from 'app/iris/exercise-chatbot/chatbot-popup/chatbot-popup.component';
import { ExerciseChatbotComponent } from 'app/iris/exercise-chatbot/exercise-chatbot.component';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { IrisSettingsUpdateComponent } from './settings/iris-settings-update/iris-settings-update.component';
import { IrisGlobalSettingsUpdateComponent } from './settings/iris-global-settings-update/iris-global-settings-update.component';
import { IrisSubSettingsUpdateComponent } from './settings/iris-settings-update/iris-sub-settings-update/iris-sub-settings-update.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { IrisCourseSettingsUpdateComponent } from 'app/iris/settings/iris-course-settings-update/iris-course-settings-update.component';
import { IrisProgrammingExerciseSettingsUpdateComponent } from 'app/iris/settings/iris-programming-exercise-settings-update/iris-programming-exercise-settings-update.component';

@NgModule({
    declarations: [
        ChatbotPopupComponent,
        ExerciseChatWidgetComponent,
        ExerciseChatbotComponent,
        IrisSettingsUpdateComponent,
        IrisGlobalSettingsUpdateComponent,
        IrisCourseSettingsUpdateComponent,
        IrisProgrammingExerciseSettingsUpdateComponent,
        IrisSubSettingsUpdateComponent,
    ],
    imports: [CommonModule, MatDialogModule, FormsModule, FontAwesomeModule, ArtemisSharedModule, ArtemisMarkdownModule, ArtemisSharedComponentModule],
    providers: [],
    exports: [ExerciseChatbotComponent],
})
export class IrisModule {}
