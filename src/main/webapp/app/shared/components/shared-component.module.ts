import { NgModule } from '@angular/core';
import { ButtonComponent, ConfirmAutofocusButtonComponent, ConfirmAutofocusModalComponent, HelpIconComponent } from './';
import { ArtemisSharedModule } from 'app/shared';

@NgModule({
    imports: [ArtemisSharedModule],
    entryComponents: [ConfirmAutofocusModalComponent],
    declarations: [ButtonComponent, HelpIconComponent, ConfirmAutofocusButtonComponent, ConfirmAutofocusModalComponent],
    exports: [ButtonComponent, HelpIconComponent, ConfirmAutofocusButtonComponent],
})
export class ArtemisSharedComponentModule {}
