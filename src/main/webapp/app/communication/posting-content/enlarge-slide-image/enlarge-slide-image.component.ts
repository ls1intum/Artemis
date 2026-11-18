import { Component, inject } from '@angular/core';
import { TumAetUiDialogConfig } from '@tumaet/ui-angular';

export interface DialogData {
    slideToReference: string;
    imageAlt: string;
}

@Component({ templateUrl: './enlarge-slide-image.component.html' })
export class EnlargeSlideImageComponent {
    data = inject(TumAetUiDialogConfig<DialogData>).data!;
}
