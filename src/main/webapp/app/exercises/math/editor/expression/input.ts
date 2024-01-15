import { Observable } from 'rxjs';
import { EventEmitter } from '@angular/core';

export interface ExpressionResult {
    expression?: string | null | undefined;
    error?: string | null | undefined;
}

export interface ExpressionChildInput<V = unknown> {
    disabled: boolean;
    exerciseId: number;

    value: V | null;
    valueChange: EventEmitter<V | null>;

    getExpression(): Observable<ExpressionResult>;
}
