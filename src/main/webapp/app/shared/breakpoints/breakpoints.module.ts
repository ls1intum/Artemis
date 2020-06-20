import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LayoutModule } from '@angular/cdk/layout';
import { LayoutService } from 'app/shared/breakpoints/layout.service';

@NgModule({
    declarations: [],
    imports: [CommonModule, LayoutModule],
    providers: [LayoutService],
})
export class BreakpointsModule {}
