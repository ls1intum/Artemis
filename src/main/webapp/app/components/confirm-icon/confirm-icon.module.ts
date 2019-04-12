import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';

import { ArTEMiSSharedModule } from 'app/shared';
import { ConfirmIconComponent } from 'app/components/confirm-icon/confirm-icon.component';

@NgModule({
    imports: [ArTEMiSSharedModule],
    declarations: [ConfirmIconComponent],
    exports: [ConfirmIconComponent],
    schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class ArTEMiSConfirmIconModule {}
