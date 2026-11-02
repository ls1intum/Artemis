/** Tracks the requested page independently of live post updates and deferred message rendering. */
export class PostPageRequestState {
    pendingScrollDistance: number | undefined;
    preserveScrollOnNextPage = false;
    private requestId = 0;

    invalidate(): void {
        this.requestId++;
    }

    captureScrollDistance(element: Pick<HTMLElement, 'scrollHeight' | 'scrollTop'> | undefined): void {
        this.pendingScrollDistance = element ? element.scrollHeight - element.scrollTop : undefined;
    }

    callbacks(content: () => Pick<HTMLElement, 'scrollHeight' | 'scrollTop'> | undefined, onComplete: (applied: boolean) => void) {
        const requestId = ++this.requestId;
        return {
            beforeApply: () => {
                if (requestId !== this.requestId || !this.preserveScrollOnNextPage) return;
                // Capture the requested response after any reader movement and intervening live updates.
                this.captureScrollDistance(content());
                this.preserveScrollOnNextPage = false;
            },
            complete: (applied: boolean) => {
                if (requestId !== this.requestId) return;
                this.preserveScrollOnNextPage = false;
                if (!applied) {
                    this.pendingScrollDistance = undefined;
                }
                onComplete(applied);
            },
        };
    }
}
