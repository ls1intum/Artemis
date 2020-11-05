import { Component, Input, OnInit } from '@angular/core';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';

@Component({
    selector: 'jhi-text-unit',
    templateUrl: './text-unit.component.html',
    styles: [],
})
export class TextUnitComponent implements OnInit {
    @Input()
    textUnit: TextUnit;

    constructor() {}

    ngOnInit(): void {}
}
