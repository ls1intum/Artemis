import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { FeatureToggleModule } from 'app/feature-toggle/feature-toggle.module';
import { ConfirmAutofocusButtonComponent, ConfirmAutofocusModalComponent } from 'app/shared/components/confirm-autofocus-button.component';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { ButtonComponent } from 'app/shared/components/button.component';

@NgModule({
    imports: [ArtemisSharedModule, FeatureToggleModule],
    entryComponents: [ConfirmAutofocusModalComponent],
    declarations: [ButtonComponent, HelpIconComponent, ConfirmAutofocusButtonComponent, ConfirmAutofocusModalComponent],
    exports: [ButtonComponent, HelpIconComponent, ConfirmAutofocusButtonComponent],
})
export class ArtemisSharedComponentModule {}
