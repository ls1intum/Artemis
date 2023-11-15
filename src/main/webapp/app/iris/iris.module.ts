import { NgModule } from '@angular/core';
import { MatDialogModule } from '@angular/material/dialog';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { AboutIrisComponent } from 'app/iris/about-iris/about-iris.component';
import { RouterModule } from '@angular/router';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { IrisSettingsUpdateComponent } from './settings/iris-settings-update/iris-settings-update.component';
import { IrisGlobalSettingsUpdateComponent } from './settings/iris-global-settings-update/iris-global-settings-update.component';
import { IrisCommonSubSettingsUpdateComponent } from './settings/iris-settings-update/iris-common-sub-settings-update/iris-common-sub-settings-update.component';
import { IrisCourseSettingsUpdateComponent } from 'app/iris/settings/iris-course-settings-update/iris-course-settings-update.component';
import { IrisExerciseSettingsUpdateComponent } from 'app/iris/settings/iris-exercise-settings-update/iris-exercise-settings-update.component';
import { IrisLogoComponent } from './iris-logo/iris-logo.component';
import { IrisChatSubSettingsUpdateComponent } from 'app/iris/settings/iris-settings-update/iris-chat-sub-settings-update/iris-chat-sub-settings-update.component';
import { IrisHestiaSubSettingsUpdateComponent } from 'app/iris/settings/iris-settings-update/iris-hestia-sub-settings-update/iris-hestia-sub-settings-update.component';
import { IrisGlobalAutoupdateSettingsUpdateComponent } from './settings/iris-settings-update/iris-global-autoupdate-settings-update/iris-global-autoupdate-settings-update.component';
import { IrisCodeEditorSubSettingsUpdateComponent } from 'app/iris/settings/iris-settings-update/iris-code-editor-sub-settings-update/iris-code-editor-sub-settings-update.component';
import { IrisCodeEditorChatbotButtonComponent } from 'app/iris/exercise-chatbot/code-editor-chatbot-button.component';
import { IrisTutorChatbotButtonComponent } from 'app/iris/exercise-chatbot/tutor-chatbot-button.component';
import { IrisCodeEditorWebsocketService } from 'app/iris/code-editor-websocket.service';
import { IrisStateStore } from 'app/iris/state-store.service';
import { IrisChatbotWidgetComponent } from 'app/iris/exercise-chatbot/widget/chatbot-widget.component';

@NgModule({
    declarations: [
        IrisChatbotWidgetComponent,
        IrisCodeEditorChatbotButtonComponent,
        IrisTutorChatbotButtonComponent,
        AboutIrisComponent,
        IrisSettingsUpdateComponent,
        IrisGlobalSettingsUpdateComponent,
        IrisCourseSettingsUpdateComponent,
        IrisExerciseSettingsUpdateComponent,
        IrisCommonSubSettingsUpdateComponent,
        IrisLogoComponent,
        IrisChatSubSettingsUpdateComponent,
        IrisHestiaSubSettingsUpdateComponent,
        IrisGlobalAutoupdateSettingsUpdateComponent,
        IrisCodeEditorSubSettingsUpdateComponent,
    ],
    imports: [CommonModule, MatDialogModule, FormsModule, FontAwesomeModule, ArtemisSharedModule, ArtemisMarkdownModule, ArtemisSharedComponentModule, RouterModule],
    providers: [IrisCodeEditorWebsocketService, IrisStateStore],
    exports: [IrisCodeEditorChatbotButtonComponent, IrisTutorChatbotButtonComponent],
})
export class IrisModule {}
