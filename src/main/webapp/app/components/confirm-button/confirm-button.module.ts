import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';

import { ArtemisSharedModule } from 'app/shared';
import { ConfirmButtonComponent } from 'app/components/confirm-button/confirm-button.component';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [ConfirmButtonComponent],
    exports: [ConfirmButtonComponent],
})
export class ArtemisConfirmButtonModule {}
