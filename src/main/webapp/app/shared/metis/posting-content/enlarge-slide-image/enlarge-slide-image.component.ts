import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';

export interface DialogData {
    slideToReference: string;
}

@Component({
    templateUrl: './enlarge-slide-image.component.html',
    standalone: true,
})
export class EnlargeSlideImageComponent {
    public data = inject(MAT_DIALOG_DATA);
}
