import { Injectable, signal } from '@angular/core';

/**
 * Pyris-provided routing hint stored at click time so the correct chat mode is
 * always used regardless of when Angular's URL signals settle.
 */
export interface IrisHandoffTarget {
    type: 'course' | 'lecture' | 'exercise';
    lectureId?: number;
    exerciseId?: number;
}

export interface IrisHandoffContext {
    query: string;
    answer: string;
    /** Routing hint from Pyris — determines which chat session to open. */
    target: IrisHandoffTarget;
}

/**
 * Transient service that carries a global-search Q&A pair across a single Angular router navigation.
 *
 * The global search Iris answer card sets this before navigating to a chat page.
 * CourseChatbotComponent reads it once in its effect and seeds a fresh session with the Q&A.
 * The context is cleared immediately after being consumed.
 *
 * `version` increments on every `set()` call so the consuming effect re-runs even when the
 * chat context (lecture/exercise/course) did not change between two consecutive Continue clicks.
 *
 * Storing the routing target here (rather than reading it from URL params inside the effect) prevents
 * a race where the effect runs before Angular has updated the route signals after navigation, causing
 * the wrong chat mode to be used and the session to snap back to the previous one.
 */
@Injectable({ providedIn: 'root' })
export class IrisHandoffContextService {
    private readonly _context = signal<IrisHandoffContext | undefined>(undefined);
    private readonly _version = signal(0);

    readonly context = this._context.asReadonly();
    /** Monotonically increasing; use as a reactive dependency to force effect re-runs. */
    readonly version = this._version.asReadonly();

    set(query: string, answer: string, target: IrisHandoffTarget): void {
        this._context.set({ query, answer, target });
        this._version.update((v) => v + 1);
    }

    clear(): void {
        this._context.set(undefined);
    }
}
