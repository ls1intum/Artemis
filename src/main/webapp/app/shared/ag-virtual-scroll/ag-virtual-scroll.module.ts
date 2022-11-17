/**
 * Module based on Open Source Project ag-virtual-scroll
 * https://github.com/ericferreira1992/ag-virtual-scroll
 *
 */

import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';

import { AgVirtualScrollComponent } from './ag-virtual-scroll.component';

@NgModule({
    imports: [CommonModule],
    declarations: [AgVirtualScrollComponent],
    exports: [AgVirtualScrollComponent],
})
export class AgVirtualScrollModule {}
