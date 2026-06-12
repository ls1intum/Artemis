import { Injectable, computed, signal } from '@angular/core';
import { ChatServiceMode, SessionContext, sameSessionContext } from 'app/iris/shared/entities/iris-session-context.model';

/**
 * Owns the (committed, pending, page) context signals and the rules that mutate them.
 * Extracted from IrisChatService so the context rules live in one focused place behind
 * intent-named methods.
 *
 * Mental model:
 * - `_committed` mirrors the server's view of the current session's context.
 * - `_pending` is a user-staged override applied on the next sendMessage. Invariant:
 *   `_pending !== undefined` ⇔ it structurally differs from `_committed`.
 * - `_page` reflects the current route (lecture/exercise/course/tutor-suggestion) and is
 *   the source-of-truth when (re)creating a session.
 */
@Injectable({ providedIn: 'root' })
export class IrisChatContextService {
    private readonly _committed = signal<SessionContext | undefined>(undefined);
    private readonly _pending = signal<SessionContext | undefined>(undefined);
    private readonly _page = signal<SessionContext | undefined>(undefined);

    readonly committed = this._committed.asReadonly();
    readonly pending = this._pending.asReadonly();
    readonly page = this._page.asReadonly();
    readonly display = computed(() => this._pending() ?? this._committed());

    setPageContext(ctx: SessionContext): void {
        this._page.set(ctx);
    }

    /**
     * Stage a user-picked context override for the next sendMessage. Dedups against both
     * `committed` (selecting the already-active context clears any pending) and the current
     * `pending` (re-staging the same option is a no-op, avoiding redundant downstream
     * computed recomputations).
     */
    stagePending(ctx: SessionContext): void {
        if (sameSessionContext(ctx, this._committed())) {
            if (this._pending() !== undefined) {
                this._pending.set(undefined);
            }
            return;
        }
        if (sameSessionContext(ctx, this._pending())) {
            return;
        }
        this._pending.set(ctx);
    }

    /**
     * Commit the context that was actually sent with a message, once the server confirms it.
     *
     * Takes the sent context explicitly rather than re-reading {@link pending} so a newer override
     * staged while the request was in flight does not get committed in its place: committed is set
     * to what the server persisted, and the newer pending is preserved (applied on the next
     * sendMessage). Pending is only cleared when it still matches the sent context — i.e. nothing
     * newer was staged.
     */
    commitSentContext(sent: SessionContext): void {
        this._committed.set(sent);
        if (sameSessionContext(this._pending(), sent)) {
            this._pending.set(undefined);
        }
    }

    /**
     * Adopt the server's view of the loaded session's context. Any in-flight user pending
     * is discarded — the server is authoritative. If we are on a non-course page (lecture /
     * exercise) whose context differs from the server's, auto-stage that page context so the
     * chip reflects the user's intent and the next sendMessage commits it.
     */
    adoptServerContext(serverCtx: SessionContext | undefined): void {
        this._pending.set(undefined);
        this._committed.set(serverCtx);

        const pageCtx = this._page();
        if (pageCtx && pageCtx.mode !== ChatServiceMode.COURSE && !sameSessionContext(pageCtx, serverCtx)) {
            this._pending.set(pageCtx);
        }
    }

    /** Logout / user switch: clear all three signals. */
    reset(): void {
        this._committed.set(undefined);
        this._pending.set(undefined);
        this._page.set(undefined);
    }
}
