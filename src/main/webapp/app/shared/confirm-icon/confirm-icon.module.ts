import { NgModule } from '@angular/core';

import { ConfirmIconComponent } from 'app/shared/confirm-icon/confirm-icon.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [ConfirmIconComponent],
    exports: [ConfirmIconComponent],
})
export class ArtemisConfirmIconModule {}
