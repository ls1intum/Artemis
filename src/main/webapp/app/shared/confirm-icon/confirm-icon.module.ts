import { NgModule } from '@angular/core';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ConfirmIconComponent } from 'app/shared/confirm-icon/confirm-icon.component';

@NgModule({
    imports: [ArtemisSharedModule, ConfirmIconComponent],
    exports: [ConfirmIconComponent],
})
export class ArtemisConfirmIconModule {}
