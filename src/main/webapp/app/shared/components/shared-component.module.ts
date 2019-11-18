import { NgModule } from '@angular/core';
import { ButtonComponent, ConfirmAutofocusButtonComponent, ConfirmAutofocusModalComponent, HelpIconComponent } from './';
import { ArtemisSharedModule } from 'app/shared';
import { FeatureToggleModule } from 'app/feature-toggle/feature-toggle.module';

@NgModule({
    imports: [ArtemisSharedModule, FeatureToggleModule],
    entryComponents: [ConfirmAutofocusModalComponent],
    declarations: [ButtonComponent, HelpIconComponent, ConfirmAutofocusButtonComponent, ConfirmAutofocusModalComponent],
    exports: [ButtonComponent, HelpIconComponent, ConfirmAutofocusButtonComponent],
})
export class ArtemisSharedComponentModule {}
