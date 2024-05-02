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
import { IrisGlobalAutoupdateSettingsUpdateComponent } from './settings/iris-settings-update/iris-global-autoupdate-settings-update/iris-global-autoupdate-settings-update.component';
import { IrisTutorChatbotButtonComponent } from 'app/iris/exercise-chatbot/tutor-chatbot-button.component';
import { IrisStateStore } from 'app/iris/state-store.service';
import { IrisChatbotWidgetComponent } from 'app/iris/exercise-chatbot/widget/chatbot-widget.component';
import { IrisEnabledComponent } from 'app/iris/settings/shared/iris-enabled.component';
import { IrisLogoButtonComponent } from 'app/iris/iris-logo-button/iris-logo-button.component';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';

@NgModule({
    declarations: [
        IrisChatbotWidgetComponent,
        IrisTutorChatbotButtonComponent,
        AboutIrisComponent,
        IrisSettingsUpdateComponent,
        IrisGlobalSettingsUpdateComponent,
        IrisCourseSettingsUpdateComponent,
        IrisExerciseSettingsUpdateComponent,
        IrisCommonSubSettingsUpdateComponent,
        IrisLogoComponent,
        IrisGlobalAutoupdateSettingsUpdateComponent,
        IrisEnabledComponent,
        IrisLogoButtonComponent,
    ],
    imports: [
        CommonModule,
        MatDialogModule,
        FormsModule,
        FontAwesomeModule,
        ArtemisSharedModule,
        ArtemisMarkdownModule,
        ArtemisSharedComponentModule,
        RouterModule,
        FeatureToggleModule,
    ],
    providers: [IrisStateStore],
    exports: [IrisTutorChatbotButtonComponent, IrisEnabledComponent, IrisLogoButtonComponent],
})
export class IrisModule {}
