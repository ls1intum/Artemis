import { NgModule } from '@angular/core';
import { MatDialogModule } from '@angular/material/dialog';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ExerciseChatWidgetComponent } from 'app/iris/exercise-chatbot/exercise-chatwidget/exercise-chat-widget.component';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ExerciseChatbotComponent } from 'app/iris/exercise-chatbot/exercise-chatbot.component';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { AboutIrisComponent } from 'app/iris/about-iris/about-iris.component';
import { RouterModule } from '@angular/router';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { IrisSettingsUpdateComponent } from './settings/iris-settings-update/iris-settings-update.component';
import { IrisGlobalSettingsUpdateComponent } from './settings/iris-global-settings-update/iris-global-settings-update.component';
import { IrisSubSettingsUpdateComponent } from './settings/iris-settings-update/iris-sub-settings-update/iris-sub-settings-update.component';
import { IrisCourseSettingsUpdateComponent } from 'app/iris/settings/iris-course-settings-update/iris-course-settings-update.component';
import { IrisProgrammingExerciseSettingsUpdateComponent } from 'app/iris/settings/iris-programming-exercise-settings-update/iris-programming-exercise-settings-update.component';
import { IrisLogoComponent } from './iris-logo/iris-logo.component';

@NgModule({
    declarations: [
        ExerciseChatWidgetComponent,
        ExerciseChatbotComponent,
        AboutIrisComponent,
        IrisSettingsUpdateComponent,
        IrisGlobalSettingsUpdateComponent,
        IrisCourseSettingsUpdateComponent,
        IrisProgrammingExerciseSettingsUpdateComponent,
        IrisSubSettingsUpdateComponent,
        IrisLogoComponent,
    ],
    imports: [CommonModule, MatDialogModule, FormsModule, FontAwesomeModule, ArtemisSharedModule, ArtemisMarkdownModule, ArtemisSharedComponentModule, RouterModule],
    providers: [],
    exports: [ExerciseChatbotComponent],
})
export class IrisModule {}
