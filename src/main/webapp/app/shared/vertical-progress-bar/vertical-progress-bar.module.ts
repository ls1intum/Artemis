import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { VerticalProgressBarComponent } from './vertical-progress-bar.component';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';

@NgModule({
    imports: [CommonModule, NgbTooltipModule],
    declarations: [VerticalProgressBarComponent],
    exports: [VerticalProgressBarComponent],
})
export class VerticalProgressBarModule {}
