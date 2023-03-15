import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';

import { ManualTextSelectionComponent } from './manual-text-selection/manual-text-selection.component';
import { TextSelectDirective } from './text-select.directive';
import { ArtemisSharedLibsModule } from 'app/shared/shared-libs.module';

@NgModule({
    imports: [CommonModule, ArtemisSharedLibsModule],
    declarations: [TextSelectDirective, ManualTextSelectionComponent],
    exports: [TextSelectDirective, ManualTextSelectionComponent],
})
export class TextSharedModule {}
