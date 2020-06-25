import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ArtemisSharedLibsModule } from 'app/shared/shared-libs.module';
import { TextSelectDirective } from './text-select.directive';
import { ManualTextSelectionComponent } from './manual-text-selection/manual-text-selection.component';

@NgModule({
    imports: [CommonModule, ArtemisSharedLibsModule],
    declarations: [TextSelectDirective, ManualTextSelectionComponent],
    exports: [TextSelectDirective, ManualTextSelectionComponent],
})
export class TextSharedModule {}
