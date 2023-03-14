import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { VerticalProgressBarComponent } from './vertical-progress-bar.component';

@NgModule({
    imports: [CommonModule],
    declarations: [VerticalProgressBarComponent],
    exports: [VerticalProgressBarComponent],
})
export class VerticalProgressBarModule {}
