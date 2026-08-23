import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SearchInputComponent } from './search-input.component';
import { FilterChipView } from '../../../models/search-menu.model';
import { MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { faCube } from '@fortawesome/free-solid-svg-icons';

describe('SearchInputComponent', () => {
    let component: SearchInputComponent;
    let fixture: ComponentFixture<SearchInputComponent>;

    const chip = (label: string): FilterChipView => ({ key: `type:${label}:false`, label, facetLabel: 'type', icon: faCube, family: 'type', negate: false, selected: false });

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [SearchInputComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(SearchInputComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('searchQuery', '');
        fixture.componentRef.setInput('chips', []);
        fixture.componentRef.setInput('isLoading', false);
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should compute hasChips from the chips input', () => {
        expect(component['hasChips']()).toBe(false);
        fixture.componentRef.setInput('chips', [chip('Exercises')]);
        expect(component['hasChips']()).toBe(true);
    });

    it('renders an outlined chip with its value and family class', () => {
        fixture.componentRef.setInput('chips', [chip('Exercises')]);
        fixture.detectChanges();
        const tok = fixture.nativeElement.querySelector('.tok');
        expect(tok).toBeTruthy();
        expect(tok.classList).toContain('tok--type');
        expect(tok.textContent).toContain('Exercises');
    });

    it('marks an exclude chip and strikes through its value', () => {
        const excluded: FilterChipView = { key: 'type:exam:true', label: 'Exams', facetLabel: 'type', icon: faCube, family: 'type', negate: true, selected: false };
        fixture.componentRef.setInput('chips', [excluded]);
        fixture.detectChanges();
        const tok = fixture.nativeElement.querySelector('.tok');
        expect(tok.classList).toContain('tok--exclude');
        expect(fixture.nativeElement.querySelector('.tok-value-text.tok-strike')).toBeTruthy();
    });

    describe('value-menu combobox a11y', () => {
        it('wires the input as a combobox pointing aria-activedescendant at the highlighted option', () => {
            // The menu listbox renders in the results pane (a sibling component); the input references it by id.
            fixture.componentRef.setInput('activeOptionId', 'gs-filter-option-type:lecture');
            fixture.componentRef.setInput('menuVisible', true);
            fixture.detectChanges();

            const input = fixture.nativeElement.querySelector('input.search-input');
            expect(input.getAttribute('role')).toBe('combobox');
            expect(input.getAttribute('aria-expanded')).toBe('true');
            expect(input.getAttribute('aria-controls')).toBe('global-search-filter-menu');
            expect(input.getAttribute('aria-activedescendant')).toBe('gs-filter-option-type:lecture');
        });

        it('drops aria-controls/activedescendant while the menu is closed', () => {
            fixture.componentRef.setInput('menuVisible', false);
            fixture.detectChanges();

            const input = fixture.nativeElement.querySelector('input.search-input');
            expect(input.getAttribute('aria-expanded')).toBe('false');
            expect(input.getAttribute('aria-controls')).toBeNull();
            expect(input.getAttribute('aria-activedescendant')).toBeNull();
        });
    });

    it('should focus input', () => {
        vi.useFakeTimers();
        const inputElement = document.createElement('input');
        vi.spyOn(component['searchInputElement']()!, 'nativeElement', 'get').mockReturnValue(inputElement);
        const spy = vi.spyOn(inputElement, 'focus');

        component.focusInput();
        vi.runAllTimers();

        expect(spy).toHaveBeenCalled();
        vi.useRealTimers();
    });

    it('should emit searchInput on input', () => {
        const spy = vi.spyOn(component.searchInput, 'emit');
        const event = { target: { value: 'test' } } as any as Event;
        component['onInput'](event);
        expect(spy).toHaveBeenCalledWith('test');
    });

    it('should emit searchKeyDown on keydown', () => {
        const spy = vi.spyOn(component.searchKeyDown, 'emit');
        const event = new KeyboardEvent('keydown');
        component['onKeyDown'](event);
        expect(spy).toHaveBeenCalledWith(event);
    });

    it('should emit chipRemoved with the chip index', () => {
        const spy = vi.spyOn(component.chipRemoved, 'emit');
        component['onChipRemove'](2);
        expect(spy).toHaveBeenCalledWith(2);
    });

    it('should emit chipSelected when a chip is clicked', () => {
        const spy = vi.spyOn(component.chipSelected, 'emit');
        component['onChipClick'](1);
        expect(spy).toHaveBeenCalledWith(1);
    });

    it.each(['Enter', ' '])('should emit chipSelected and stop propagation when "%s" is pressed on a focused chip', (key) => {
        const spy = vi.spyOn(component.chipSelected, 'emit');
        const event = new KeyboardEvent('keydown', { key });
        const preventDefaultSpy = vi.spyOn(event, 'preventDefault');
        const stopPropagationSpy = vi.spyOn(event, 'stopPropagation');

        component['onChipKeydown'](2, event);

        expect(spy).toHaveBeenCalledWith(2);
        expect(preventDefaultSpy).toHaveBeenCalled();
        expect(stopPropagationSpy).toHaveBeenCalled();
    });

    it('should ignore other keys on a focused chip', () => {
        const spy = vi.spyOn(component.chipSelected, 'emit');
        component['onChipKeydown'](2, new KeyboardEvent('keydown', { key: 'a' }));
        expect(spy).not.toHaveBeenCalled();
    });

    it('should emit backspaceOnEmpty when Backspace is pressed on empty input', () => {
        const spy = vi.spyOn(component.backspaceOnEmpty, 'emit');
        fixture.detectChanges();
        const event = new KeyboardEvent('keydown', { key: 'Backspace' });
        component['onKeyDown'](event);
        expect(spy).toHaveBeenCalled();
    });

    it('should not emit backspaceOnEmpty when Backspace is pressed with cursor not at beginning', () => {
        const spy = vi.spyOn(component.backspaceOnEmpty, 'emit');
        const inputEl = fixture.nativeElement.querySelector('.search-input') as HTMLInputElement;
        inputEl.value = 'a';
        inputEl.selectionStart = 1;
        inputEl.selectionEnd = 1;
        const event = new KeyboardEvent('keydown', { key: 'Backspace' });
        component['onKeyDown'](event);
        expect(spy).not.toHaveBeenCalled();
    });

    it('should emit backspaceOnEmpty when Backspace is pressed with cursor at beginning of non-empty input', () => {
        const spy = vi.spyOn(component.backspaceOnEmpty, 'emit');
        const inputEl = fixture.nativeElement.querySelector('.search-input') as HTMLInputElement;
        inputEl.value = 'hello';
        inputEl.selectionStart = 0;
        inputEl.selectionEnd = 0;
        const event = new KeyboardEvent('keydown', { key: 'Backspace' });
        component['onKeyDown'](event);
        expect(spy).toHaveBeenCalled();
    });

    it('should not emit backspaceOnEmpty when Backspace is pressed with text selected from beginning', () => {
        const spy = vi.spyOn(component.backspaceOnEmpty, 'emit');
        const inputEl = fixture.nativeElement.querySelector('.search-input') as HTMLInputElement;
        inputEl.value = 'hello';
        inputEl.selectionStart = 0;
        inputEl.selectionEnd = 3;
        const event = new KeyboardEvent('keydown', { key: 'Backspace' });
        component['onKeyDown'](event);
        expect(spy).not.toHaveBeenCalled();
    });

    it('paints only the trailing operator and leaves the search text in front of it in ordinary ink', () => {
        fixture.componentRef.setInput('searchQuery', 'linear regression type:lec');
        fixture.componentRef.setInput('operator', { facet: 'type', negate: false, query: 'lec', prefix: 'type:', start: 18, text: 'linear regression' });
        fixture.detectChanges();

        const highlight = fixture.nativeElement.querySelector('.search-highlight-text');
        expect(highlight.textContent).toBe('linear regression type:lec');
        expect(highlight.querySelector('.op-tag').textContent).toBe('type:');
        expect(component['leadingText']()).toBe('linear regression ');
    });

    it('marks a value that matches no filter without colouring it as an error', () => {
        fixture.componentRef.setInput('searchQuery', 'type:candle');
        fixture.componentRef.setInput('operator', { facet: 'type', negate: false, query: 'candle', prefix: 'type:', start: 0, text: '' });
        fixture.componentRef.setInput('operatorUnknown', true);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('.op-value').classList).toContain('op-unknown');
    });
});
