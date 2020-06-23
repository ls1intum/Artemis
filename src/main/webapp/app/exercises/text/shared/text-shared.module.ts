import { NgModule } from '@angular/core';
import { TextSelectDirective } from './text-select.directive';

@NgModule({
    declarations: [TextSelectDirective],
    exports: [TextSelectDirective],
})
export class TextSharedModule {}
