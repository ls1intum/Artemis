import { Injectable, Signal, computed, signal } from '@angular/core';
import { TumUiSize } from '../foundation/tum-ui-vocabulary';

/** Shares the group's size signal with projected items; standalone items use their own default. */
@Injectable()
export class TumUiItemGroupService {
    private readonly source = signal<Signal<TumUiSize>>(signal<TumUiSize>('medium'));

    /** Row size the enclosing group currently requests. */
    readonly size = computed<TumUiSize>(() => this.source()());

    register(size: Signal<TumUiSize>): void {
        this.source.set(size);
    }
}
