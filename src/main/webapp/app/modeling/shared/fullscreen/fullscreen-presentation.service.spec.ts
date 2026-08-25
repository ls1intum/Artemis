import { TestBed } from '@angular/core/testing';
import { FullscreenPresentationService } from 'app/modeling/shared/fullscreen/fullscreen-presentation.service';

describe('FullscreenPresentationService', () => {
    let service: FullscreenPresentationService;
    let parent: HTMLElement | undefined;

    beforeEach(() => {
        service = TestBed.inject(FullscreenPresentationService);
    });

    afterEach(() => {
        service.restore();
        parent?.remove();
        parent = undefined;
    });

    it('tracks the promoted editor by identity and restores its exact DOM position', () => {
        parent = document.createElement('div');
        const editor = document.createElement('div');
        const sibling = document.createElement('div');
        parent.append(editor, sibling);
        document.body.append(parent);

        expect(service.promote(editor)).toBe(true);
        expect(service.owns(editor)).toBe(true);
        expect(service.owns(sibling)).toBe(false);
        expect(editor.parentElement).toBe(document.body);
        expect(service.promote(sibling)).toBe(false);

        service.restore();

        expect(service.owns(editor)).toBe(false);
        expect(Array.from(parent.children)).toEqual([editor, sibling]);
        expect(editor.nextElementSibling).toBe(sibling);
    });

    // Regression for the exam page switcher: the exam hides the previous exercise with `[hidden]` on a wrapper the
    // promoted frame no longer descends from, so nothing could hide it any more. Switching exercises left the editor
    // pinned fullscreen over the whole app, intercepting every click, recoverable only by exiting on the stuck frame.
    it('escapes when the slot the editor came from stops being shown', () => {
        parent = document.createElement('div');
        const editor = document.createElement('div');
        parent.append(editor);
        document.body.append(parent);
        const observed: Element[] = [];
        let fireIntersection: () => void = () => {};
        vi.stubGlobal(
            'IntersectionObserver',
            class {
                constructor(callback: () => void) {
                    fireIntersection = callback;
                }
                observe(target: Element) {
                    observed.push(target);
                }
                disconnect() {}
            },
        );
        // jsdom has no layout, so the visibility probe has to be driven explicitly.
        const checkVisibility = vi.fn().mockReturnValue(true);
        (parent as unknown as { checkVisibility: () => boolean }).checkVisibility = checkVisibility;
        const onEscape = vi.fn();

        service.promote(editor, onEscape);
        expect(observed).toContain(parent);

        // Still shown, merely re-measured (e.g. the page scrolled): not an escape.
        fireIntersection();
        expect(onEscape).not.toHaveBeenCalled();

        checkVisibility.mockReturnValue(false);
        fireIntersection();

        expect(onEscape).toHaveBeenCalledOnce();
        vi.unstubAllGlobals();
    });

    // Regression for a `readOnly` flip: the frame's `@if` is torn down while the document is still fullscreen, which
    // left the browser fullscreen on nothing at all.
    it('escapes when the promoted editor is destroyed underneath it', async () => {
        parent = document.createElement('div');
        const editor = document.createElement('div');
        parent.append(editor);
        document.body.append(parent);
        const onEscape = vi.fn();

        service.promote(editor, onEscape);
        expect(editor.parentElement).toBe(document.body);

        editor.remove();
        await vi.waitFor(() => expect(onEscape).toHaveBeenCalledOnce());
    });

    it('does not resurrect a destroyed editor into the view that dropped it', () => {
        parent = document.createElement('div');
        const editor = document.createElement('div');
        parent.append(editor);
        document.body.append(parent);

        service.promote(editor);
        editor.remove();
        service.restore();

        expect(parent.contains(editor)).toBe(false);
        expect(service.owns(editor)).toBe(false);
    });

    it('removes a promoted editor whose original parent was detached and restores idempotently', () => {
        parent = document.createElement('div');
        const editor = document.createElement('div');
        parent.append(editor);
        document.body.append(parent);

        expect(service.promote(editor)).toBe(true);
        parent.remove();
        service.restore();
        service.restore();

        expect(editor.isConnected).toBe(false);
        expect(service.owns(editor)).toBe(false);
    });
});
