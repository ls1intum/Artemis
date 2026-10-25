import { vi } from 'vitest';
import { PostPageRequestState } from './post-page-request-state';

describe('PostPageRequestState', () => {
    it('captures the latest reader position immediately before the requested page is applied', () => {
        const state = new PostPageRequestState();
        const viewport = { scrollHeight: 1000, scrollTop: 200 };
        const content = vi.fn(() => viewport);
        state.preserveScrollOnNextPage = true;
        const complete = vi.fn();
        const callbacks = state.callbacks(content, complete);
        viewport.scrollHeight = 1200;
        viewport.scrollTop = 350;
        expect(content).not.toHaveBeenCalled();
        callbacks.beforeApply();
        expect(state.pendingScrollDistance).toBe(850);
        expect(state.preserveScrollOnNextPage).toBe(false);
        callbacks.beforeApply();
        expect(content).toHaveBeenCalledOnce();
        callbacks.complete(true);
        expect(complete).toHaveBeenCalledExactlyOnceWith(true);
        expect(state.pendingScrollDistance).toBe(850);
    });

    it('ignores superseded requests without consuming the current request anchor or completion', () => {
        const state = new PostPageRequestState();
        const oldContent = vi.fn();
        const oldComplete = vi.fn();
        const old = state.callbacks(oldContent, oldComplete);
        const complete = vi.fn();
        const current = state.callbacks(() => ({ scrollHeight: 500, scrollTop: 100 }), complete);
        state.preserveScrollOnNextPage = true;
        old.beforeApply();
        old.complete(false);
        expect(oldContent).not.toHaveBeenCalled();
        expect(oldComplete).not.toHaveBeenCalled();
        expect(state.preserveScrollOnNextPage).toBe(true);
        current.beforeApply();
        current.complete(true);
        expect(state.pendingScrollDistance).toBe(400);
        expect(complete).toHaveBeenCalledExactlyOnceWith(true);
    });

    it('clears a failed page anchor before asking the caller to roll back its page number', () => {
        const state = new PostPageRequestState();
        state.pendingScrollDistance = 300;
        state.preserveScrollOnNextPage = true;
        const complete = vi.fn((applied: boolean) => {
            expect(applied).toBe(false);
            expect(state.pendingScrollDistance).toBeUndefined();
            expect(state.preserveScrollOnNextPage).toBe(false);
        });
        state.callbacks(() => undefined, complete).complete(false);
        expect(complete).toHaveBeenCalledOnce();
    });

    it('drops callbacks invalidated by component destruction', () => {
        const state = new PostPageRequestState();
        const content = vi.fn();
        const complete = vi.fn();
        const callbacks = state.callbacks(content, complete);
        state.preserveScrollOnNextPage = true;
        state.invalidate();
        callbacks.beforeApply();
        callbacks.complete(true);
        expect(content).not.toHaveBeenCalled();
        expect(complete).not.toHaveBeenCalled();
    });

    it('can refresh an existing anchor while forwarded sources are being prepared', () => {
        const state = new PostPageRequestState();
        state.captureScrollDistance({ scrollHeight: 1000, scrollTop: 250 });
        expect(state.pendingScrollDistance).toBe(750);
        state.captureScrollDistance({ scrollHeight: 1000, scrollTop: 400 });
        expect(state.pendingScrollDistance).toBe(600);
        state.captureScrollDistance(undefined);
        expect(state.pendingScrollDistance).toBeUndefined();
    });

    it('does not anchor a request that is navigating to a target instead of preserving reader scrolling', () => {
        const state = new PostPageRequestState();
        const content = vi.fn();
        const callbacks = state.callbacks(content, vi.fn());
        callbacks.beforeApply();
        expect(content).not.toHaveBeenCalled();
        state.preserveScrollOnNextPage = true;
        callbacks.beforeApply();
        expect(state.pendingScrollDistance).toBeUndefined();
        expect(state.preserveScrollOnNextPage).toBe(false);
    });
});
