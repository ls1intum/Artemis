import { Component, OnInit } from '@angular/core';

@Component({
    selector: 'jhi-pdf-preview-component',
    templateUrl: './pdf-preview.component.html',
    styleUrls: ['./pdf-preview.component.scss'],
})
export class PdfPreviewComponent implements OnInit {
    imageUrls: string[] = [];

    constructor() {}

    ngOnInit() {
        this.imageUrls = [
            'https://via.placeholder.com/200x150.png?text=Page+1',
            'https://via.placeholder.com/200x150.png?text=Page+2',
            'https://via.placeholder.com/200x150.png?text=Page+3',
            'https://via.placeholder.com/200x150.png?text=Page+4',
            'https://via.placeholder.com/200x150.png?text=Page+5',
        ];
    }
}
