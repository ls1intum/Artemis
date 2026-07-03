import { Component, inject } from '@angular/core';
import { DynamicDialogConfig } from 'primeng/dynamicdialog';

export interface DialogData {
    slideToReference: string;
}

@Component({ templateUrl: './enlarge-slide-image.component.html' })
export class EnlargeSlideImageComponent {
    data = inject<DynamicDialogConfig<DialogData>>(DynamicDialogConfig).data!;
}
