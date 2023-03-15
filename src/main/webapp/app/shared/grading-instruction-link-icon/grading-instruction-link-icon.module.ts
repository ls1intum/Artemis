import { NgModule } from '@angular/core';

import { GradingInstructionLinkIconComponent } from 'app/shared/grading-instruction-link-icon/grading-instruction-link-icon.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [GradingInstructionLinkIconComponent],
    exports: [GradingInstructionLinkIconComponent],
})
export class ArtemisGradingInstructionLinkIconModule {}
