import { Injectable, signal } from '@angular/core';

export interface IrisHandoffContext {
    query: string;
    answer: string;
}

/**
 * Transient service that carries a global-search Q&A pair across a single Angular router navigation.
 *
 * The global search Iris answer card sets this before navigating to a chat page.
 * The target chat component reads it once on init to prepend the exchange as virtual
 * seed messages, giving the user immediate visual continuity without re-running the pipeline.
 * The context is cleared as soon as real session messages arrive.
 */
@Injectable({ providedIn: 'root' })
export class IrisHandoffContextService {
    private readonly _context = signal<IrisHandoffContext | undefined>(undefined);
    readonly context = this._context.asReadonly();

    set(query: string, answer: string): void {
        this._context.set({ query, answer });
    }

    clear(): void {
        this._context.set(undefined);
    }
}
