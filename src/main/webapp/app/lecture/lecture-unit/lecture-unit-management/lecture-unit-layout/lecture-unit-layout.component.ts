import { Component, Input } from '@angular/core';

@Component({
    selector: 'jhi-lecture-unit-layout',
    templateUrl: './lecture-unit-layout.component.html',
    styles: [],
    standalone: false,
})
export class LectureUnitLayoutComponent {
    @Input() isLoading = false;
}
