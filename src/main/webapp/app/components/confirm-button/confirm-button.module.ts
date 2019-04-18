import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';

import { ArTEMiSSharedModule } from 'app/shared';
import { ConfirmButtonComponent } from 'app/components/confirm-button/confirm-button.component';

@NgModule({
    imports: [ArTEMiSSharedModule],
    declarations: [ConfirmButtonComponent],
    exports: [ConfirmButtonComponent],
    schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class ArTEMiSConfirmButtonModule {}
