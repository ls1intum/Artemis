import { AfterViewInit, DestroyRef, Directive, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';

/** Fragment navigation shared by asynchronously rendered legal documents. */
@Directive()
export abstract class LegalDocumentFragmentDirective implements AfterViewInit {
    private readonly route = inject(ActivatedRoute);
    private readonly fragmentDestroyRef = inject(DestroyRef);
    private currentFragmentId: string | undefined;

    /**
     * After view initialization scroll the fragment of the current route into view.
     */
    ngAfterViewInit(): void {
        this.route.fragment.pipe(takeUntilDestroyed(this.fragmentDestroyRef)).subscribe((fragmentId) => {
            this.currentFragmentId = fragmentId ?? undefined;
            this.scrollToFragment(this.currentFragmentId);
        });
    }

    /** Re-attempt fragment scrolling once the lazy markdown pipeline has produced the document. */
    protected onMarkdownRendered(): void {
        this.scrollToFragment(this.currentFragmentId);
    }

    /**
     * Scrolls to the anchor with the given id. The document is rendered asynchronously (HTTP load + lazy
     * markdown rendering), so the anchor may not exist yet; retry over a few animation frames until it does.
     */
    private scrollToFragment(fragmentId: string | undefined, attemptsLeft = 20): void {
        if (!fragmentId) {
            return;
        }
        try {
            const fragment = document.querySelector('#' + fragmentId);
            if (fragment !== null) {
                fragment.scrollIntoView();
                return;
            }
        } catch (e) {
            return; // invalid selector — nothing to scroll to
        }
        if (attemptsLeft > 0) {
            requestAnimationFrame(() => this.scrollToFragment(fragmentId, attemptsLeft - 1));
        }
    }
}
