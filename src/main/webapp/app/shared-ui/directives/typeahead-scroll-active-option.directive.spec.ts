import { Mock, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbTypeahead } from '@ng-bootstrap/ng-bootstrap';
import { Observable, map } from 'rxjs';
import { TypeaheadScrollActiveOptionDirective } from 'app/shared-ui/directives/typeahead-scroll-active-option.directive';

const OPTIONS = ['Ada', 'Alan', 'Barbara', 'Donald', 'Edsger', 'Grace'];

// A host is needed: the directive works together with NgbTypeahead on the same input, which createDirective cannot add.
@Component({
    template: `<input [ngbTypeahead]="search" [focusFirst]="focusFirst()" [container]="'body'" jhiTypeaheadScrollActiveOption />`,
    imports: [NgbTypeahead, TypeaheadScrollActiveOptionDirective],
})
class TypeaheadHostComponent {
    readonly focusFirst = signal(true);
    readonly search = (text$: Observable<string>) => text$.pipe(map(() => OPTIONS));
}

describe('TypeaheadScrollActiveOptionDirective', () => {
    // jsdom does not lay out the page, so it has no scrollIntoView.
    const originalScrollIntoView = Element.prototype.scrollIntoView;
    let scrollIntoView: Mock<Element['scrollIntoView']>;
    let fixture: ComponentFixture<TypeaheadHostComponent>;
    let input: HTMLInputElement;

    beforeEach(async () => {
        scrollIntoView = vi.fn();
        Element.prototype.scrollIntoView = scrollIntoView;
        fixture = TestBed.createComponent(TypeaheadHostComponent);
        await fixture.whenStable();
        input = fixture.nativeElement.querySelector('input');
    });

    afterEach(() => {
        Element.prototype.scrollIntoView = originalScrollIntoView;
        fixture.destroy();
    });

    async function openPopup(): Promise<void> {
        input.value = 'a';
        input.dispatchEvent(new Event('input'));
        await fixture.whenStable();
    }

    async function press(key: string): Promise<void> {
        input.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
        await fixture.whenStable();
    }

    function options(): HTMLElement[] {
        return Array.from(document.querySelectorAll<HTMLElement>('ngb-typeahead-window button.dropdown-item'));
    }

    it('scrolls the option that ArrowDown activates into view', async () => {
        await openPopup();

        await press('ArrowDown');
        await press('ArrowDown');
        await press('ArrowDown');

        expect(options()[3].classList).toContain('active');
        expect(scrollIntoView).toHaveBeenCalledTimes(3);
        expect(scrollIntoView.mock.contexts.at(-1)).toBe(options()[3]);
        expect(scrollIntoView).toHaveBeenLastCalledWith({ block: 'nearest' });
    });

    it('scrolls the last option into view when ArrowUp wraps around from the first', async () => {
        await openPopup();

        await press('ArrowUp');

        expect(options()[OPTIONS.length - 1].classList).toContain('active');
        expect(scrollIntoView).toHaveBeenCalledOnce();
        expect(scrollIntoView.mock.contexts[0]).toBe(options()[OPTIONS.length - 1]);
    });

    it('does not scroll when ArrowDown leaves the last option and no option is active', async () => {
        fixture.componentInstance.focusFirst.set(false);
        await fixture.whenStable();
        await openPopup();
        await press('ArrowUp');
        scrollIntoView.mockClear();

        await press('ArrowDown');

        expect(options().some((option) => option.classList.contains('active'))).toBe(false);
        expect(scrollIntoView).not.toHaveBeenCalled();
    });

    it('does not scroll when the pointer activates an option', async () => {
        await openPopup();

        options()[4].dispatchEvent(new MouseEvent('mouseenter'));
        await fixture.whenStable();

        expect(options()[4].classList).toContain('active');
        expect(scrollIntoView).not.toHaveBeenCalled();
    });

    it('does not scroll back to the active option when keys edit the search text', async () => {
        await openPopup();
        await press('ArrowDown');
        scrollIntoView.mockClear();

        await press('ArrowLeft');
        await press('Home');

        expect(options()[1].classList).toContain('active');
        expect(scrollIntoView).not.toHaveBeenCalled();
    });
});
