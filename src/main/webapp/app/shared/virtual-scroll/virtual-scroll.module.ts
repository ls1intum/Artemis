/**
 * Module based on Open Source Project ag-virtual-scroll
 * https://github.com/ericferreira1992/ag-virtual-scroll
 *
 */

import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';

import { VirtualScrollComponent } from './virtual-scroll.component';

@NgModule({
    imports: [CommonModule],
    declarations: [VirtualScrollComponent],
    exports: [VirtualScrollComponent],
})
export class VirtualScrollModule {}
