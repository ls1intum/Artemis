import { NgModule } from '@angular/core';
import { FormStatusBarComponent } from 'app/forms/form-status-bar/form-status-bar.component';

import { FormFooterComponent } from 'app/forms/form-footer/form-footer.component';
import { ExerciseUpdateNotificationModule } from 'app/exercises/shared/exercise-update-notification/exercise-update-notification.module';
import { SwitchEditModeButtonComponent } from 'app/exercises/programming/manage/update/switch-edit-mode-button/switch-edit-mode-button.component';

@NgModule({
    imports: [ExerciseUpdateNotificationModule, SwitchEditModeButtonComponent, FormStatusBarComponent, FormFooterComponent],
    exports: [FormStatusBarComponent, FormFooterComponent],
})
export class ArtemisFormsModule {}
