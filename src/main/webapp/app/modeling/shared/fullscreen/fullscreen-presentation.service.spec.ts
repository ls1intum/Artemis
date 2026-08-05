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
