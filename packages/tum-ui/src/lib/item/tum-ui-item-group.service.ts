import { Injectable, Signal, computed, signal } from '@angular/core';
import { TumUiSize } from '../foundation/tum-ui-vocabulary';

/**
 * Publishes the group's row size to the items it projects.
 *
 * An item is declared in the consumer's template, so it cannot read the group's `size` input directly. The group
 * registers that input here once and every item reads the shared signal back, which is what keeps forty rows the
 * same height without the consumer repeating `size` forty times. An item used without a group injects nothing and
 * falls back to the default.
 */
@Injectable()
export class TumUiItemGroupService {
    private readonly source = signal<Signal<TumUiSize>>(signal<TumUiSize>('medium'));

    /** Row size the enclosing group currently requests. */
    readonly size = computed<TumUiSize>(() => this.source()());

    register(size: Signal<TumUiSize>): void {
        this.source.set(size);
    }
}
