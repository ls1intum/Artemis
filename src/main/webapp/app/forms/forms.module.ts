import { NgModule } from '@angular/core';
import { FormStatusBarComponent } from 'app/forms/form-status-bar/form-status-bar.component';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FormFooterComponent } from 'app/forms/form-footer/form-footer.component';
import { ExerciseUpdateNotificationModule } from 'app/exercises/shared/exercise-update-notification/exercise-update-notification.module';
import { SwitchEditModeButtonComponent } from 'app/exercises/programming/manage/update/switch-edit-mode-button/switch-edit-mode-button.component';
import { NgbTooltipMocksModule } from '../../../../test/javascript/spec/helpers/mocks/directive/ngbTooltipMocks.module';

@NgModule({
    imports: [ArtemisSharedCommonModule, ArtemisSharedComponentModule, ExerciseUpdateNotificationModule, SwitchEditModeButtonComponent, NgbTooltipMocksModule],
    declarations: [FormStatusBarComponent, FormFooterComponent],
    exports: [FormStatusBarComponent, FormFooterComponent],
})
export class FormsModule {}
