import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HighlightedTextAreaComponent } from 'app/text-shared/highlighted-text-area/highlighted-text-area.component';

@NgModule({
    declarations: [HighlightedTextAreaComponent],
    imports: [CommonModule],
    exports: [HighlightedTextAreaComponent]
})
export class TextSharedModule {}
