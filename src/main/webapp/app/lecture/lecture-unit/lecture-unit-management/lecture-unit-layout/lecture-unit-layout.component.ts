import { Component, Input, OnInit } from '@angular/core';

@Component({
    selector: 'jhi-lecture-unit-layout',
    templateUrl: './lecture-unit-layout.component.html',
    styles: [],
})
export class LectureUnitLayoutComponent implements OnInit {
    @Input()
    isLoading = false;

    constructor() {}

    ngOnInit(): void {}
}
