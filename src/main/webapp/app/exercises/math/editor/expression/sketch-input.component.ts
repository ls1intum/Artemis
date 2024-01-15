import { Component, EventEmitter, Input, Output } from '@angular/core';

import { MathOcrService } from './math-ocr.service';
import { ExpressionChildInput } from './input';
import { EMPTY, Observable } from 'rxjs';

export interface Strokes {
    x: number[][];
    y: number[][];
}

@Component({
    selector: 'jhi-math-task-expression-editor-sketch-input',
    templateUrl: './sketch-input.component.html',
    styleUrl: './sketch-input.component.scss',
})
export class SketchInputComponent implements ExpressionChildInput<Strokes> {
    protected get strokes(): Strokes | null {
        return this.value;
    }

    protected set strokes(value: Strokes | null) {
        this.value = value;
        this.valueChange.emit(value);
    }

    @Input()
    disabled: boolean;

    @Input()
    exerciseId: number;

    @Input()
    value: Strokes | null;

    @Output()
    valueChange = new EventEmitter<Strokes | null>();

    constructor(private service: MathOcrService) {}

    getExpression(): Observable<string | null> {
        return EMPTY;
    }
}
