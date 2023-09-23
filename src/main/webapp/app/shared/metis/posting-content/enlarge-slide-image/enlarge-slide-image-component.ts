import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';

export interface DialogData {
    slideToReference: string;
}

@Component({
    templateUrl: './enlarge-slide-image-component.html',
})
export class EnlargeSlideImageComponent {
    constructor(@Inject(MAT_DIALOG_DATA) public data: DialogData) {}
}
