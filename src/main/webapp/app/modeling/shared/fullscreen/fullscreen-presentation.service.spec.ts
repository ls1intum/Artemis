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
        const checkVisibility = vi.fn().mockReturnValue(true);
        (parent as unknown as { checkVisibility: () => boolean }).checkVisibility = checkVisibility;
        const onEscape = vi.fn();

        service.promote(editor, onEscape);
        expect(observed).toContain(parent);

        fireIntersection();
        expect(onEscape).not.toHaveBeenCalled();

        checkVisibility.mockReturnValue(false);
        fireIntersection();

        expect(onEscape).toHaveBeenCalledOnce();
        vi.unstubAllGlobals();
    });

    it('allows a fresh promotion after an escaped presentation is restored', () => {
        parent = document.createElement('div');
        const editor = document.createElement('div');
        const nextEditor = document.createElement('div');
        parent.append(editor, nextEditor);
        document.body.append(parent);
        let escape = () => {};
        vi.stubGlobal(
            'IntersectionObserver',
            class {
                constructor(callback: () => void) {
                    escape = callback;
                }
                observe() {}
                disconnect() {}
            },
        );
        (parent as unknown as { checkVisibility: () => boolean }).checkVisibility = () => false;

        service.promote(editor, () => service.restore());
        escape();

        expect(service.promote(nextEditor)).toBe(true);
        expect(service.owns(nextEditor)).toBe(true);
        vi.unstubAllGlobals();
    });

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
