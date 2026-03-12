import { Component, EventEmitter, Input, NgModule, Output } from '@angular/core';

@Component({
    selector: 'pdf-viewer',
    standalone: true,
    template: '',
})
export class MockPdfViewerComponent {
    @Input() src?: string;
    @Input() renderText?: boolean;
    @Input() showAll?: boolean;
    @Input() originalSize?: boolean;
    @Input() zoom?: number;
    @Input() zoomScale?: string;
    @Input() page?: number;
    @Output() pageChange = new EventEmitter<number>();
    @Output('after-load-complete') afterLoadComplete = new EventEmitter<unknown>();
    @Output('error') onError = new EventEmitter<unknown>();
}

@NgModule({
    imports: [MockPdfViewerComponent],
    exports: [MockPdfViewerComponent],
})
export class MockPdfViewerModule {}
