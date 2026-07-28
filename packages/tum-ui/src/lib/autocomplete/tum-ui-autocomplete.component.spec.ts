import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { vi } from 'vitest';
import { TumUiAutoCompleteComponent } from './tum-ui-autocomplete.component';

describe('TumUiAutoCompleteComponent (multiple mode)', () => {
    let component: TumUiAutoCompleteComponent;
    let fixture: ComponentFixture<TumUiAutoCompleteComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [TumUiAutoCompleteComponent, FontAwesomeTestingModule] }).compileComponents();
        fixture = TestBed.createComponent(TumUiAutoCompleteComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('multiple', true);
        fixture.componentRef.setInput('delay', 0);
        fixture.detectChanges();
    });

    afterEach(() => {
        fixture.destroy();
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    const flush = () => new Promise((resolve) => setTimeout(resolve, 0));

    function input(): HTMLInputElement {
        return fixture.debugElement.query(By.css('input[role="combobox"]')).nativeElement;
    }
    function typeQuery(text: string): void {
        const el = input();
        el.value = text;
        el.dispatchEvent(new Event('input', { bubbles: true }));
    }
    function listbox(): HTMLElement | null {
        return document.querySelector('[role="listbox"]');
    }
    function options(): HTMLElement[] {
        return Array.from(document.querySelectorAll('[role="option"]'));
    }
    function chips(): HTMLElement[] {
        return Array.from(fixture.nativeElement.querySelectorAll('tum-ui-chip'));
    }
    async function search(text: string, suggestions: unknown[]): Promise<void> {
        input().dispatchEvent(new FocusEvent('focus'));
        typeQuery(text);
        await flush();
        fixture.componentRef.setInput('suggestions', suggestions);
        fixture.detectChanges();
    }

    it('emits the debounced complete query once rapid keystrokes settle', () => {
        vi.useFakeTimers();
        fixture.componentRef.setInput('delay', 300);
        const emitSpy = vi.spyOn(component.completeMethod, 'emit');
        typeQuery('a');
        vi.advanceTimersByTime(100);
        typeQuery('ad');
        vi.advanceTimersByTime(299);
        expect(emitSpy).not.toHaveBeenCalled();
        vi.advanceTimersByTime(1);
        expect(emitSpy).toHaveBeenCalledTimes(1);
        expect(emitSpy).toHaveBeenCalledWith(expect.objectContaining({ query: 'ad' }));
    });

    it('does not emit the complete query below minLength', () => {
        vi.useFakeTimers();
        fixture.componentRef.setInput('minLength', 2);
        fixture.componentRef.setInput('delay', 300);
        const emitSpy = vi.spyOn(component.completeMethod, 'emit');
        typeQuery('a');
        vi.advanceTimersByTime(500);
        expect(emitSpy).not.toHaveBeenCalled();
    });

    it('renders the pushed suggestions as a role="listbox" of role="option"s and wires aria-expanded/controls', async () => {
        await search('a', ['admin', 'artemis']);
        expect(listbox()).not.toBeNull();
        expect(listbox()!.getAttribute('role')).toBe('listbox');
        const opts = options();
        expect(opts).toHaveLength(2);
        expect(opts.map((o) => o.getAttribute('role'))).toEqual(['option', 'option']);
        expect(opts[0].textContent?.trim()).toBe('admin');
        expect(input().getAttribute('aria-expanded')).toBe('true');
        expect(input().getAttribute('aria-controls')).toBe(listbox()!.id);
    });

    it('selecting a suggestion adds a chip, pushes the array through the CVA, emits onSelect, and closes the panel', async () => {
        const onChangeCallback = vi.fn();
        component.registerOnChange(onChangeCallback);
        const selectSpy = vi.spyOn(component.onSelect, 'emit');
        await search('ad', ['admin', 'artemis']);
        options()[0].click();
        fixture.detectChanges();

        expect(onChangeCallback).toHaveBeenCalledWith(['admin']);
        expect(selectSpy).toHaveBeenCalledWith(expect.objectContaining({ value: 'admin' }));
        expect(chips()).toHaveLength(1);
        expect(chips()[0].textContent?.trim()).toBe('admin');
        expect(listbox()).toBeNull();
        expect(input().value).toBe('');
    });

    it('does not add a duplicate suggestion', async () => {
        const onChangeCallback = vi.fn();
        component.registerOnChange(onChangeCallback);
        component.writeValue(['admin']);
        fixture.detectChanges();
        await search('ad', ['admin']);
        options()[0].click();
        fixture.detectChanges();
        expect(chips()).toHaveLength(1);
        expect(onChangeCallback).not.toHaveBeenCalled();
    });

    it('removing a chip emits onUnselect and updates the CVA value', async () => {
        const onChangeCallback = vi.fn();
        component.registerOnChange(onChangeCallback);
        const unselectSpy = vi.spyOn(component.onUnselect, 'emit');
        component.writeValue(['admin', 'artemis']);
        fixture.detectChanges();
        const removeButton = chips()[0].querySelector('button') as HTMLButtonElement;
        removeButton.click();
        fixture.detectChanges();

        expect(unselectSpy).toHaveBeenCalledWith(expect.objectContaining({ value: 'admin' }));
        expect(onChangeCallback).toHaveBeenCalledWith(['artemis']);
        expect(chips()).toHaveLength(1);
        expect(chips()[0].textContent?.trim()).toBe('artemis');
    });

    it('Backspace on an empty input removes the last chip', () => {
        const unselectSpy = vi.spyOn(component.onUnselect, 'emit');
        component.writeValue(['admin', 'artemis']);
        fixture.detectChanges();
        input().dispatchEvent(new KeyboardEvent('keydown', { key: 'Backspace', bubbles: true }));
        fixture.detectChanges();
        expect(unselectSpy).toHaveBeenCalledWith(expect.objectContaining({ value: 'artemis' }));
        expect(chips().map((c) => c.textContent?.trim())).toEqual(['admin']);
    });

    it('keyboard: ArrowDown activates an option (aria-activedescendant) and Enter selects it', async () => {
        const selectSpy = vi.spyOn(component.onSelect, 'emit');
        await search('a', ['admin', 'artemis']);
        input().dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }));
        fixture.detectChanges();
        expect(input().getAttribute('aria-activedescendant')).toBe(options()[0].id);
        input().dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
        fixture.detectChanges();
        expect(selectSpy).toHaveBeenCalledWith(expect.objectContaining({ value: 'admin' }));
    });

    it('shows the empty message when a search returns no suggestions', async () => {
        fixture.componentRef.setInput('emptyMessage', 'Nothing found');
        await search('zzz', []);
        const emptyOption = options()[0];
        expect(emptyOption.textContent?.trim()).toBe('Nothing found');
        expect(emptyOption.getAttribute('aria-disabled')).toBe('true');
        expect(emptyOption.getAttribute('aria-selected')).toBe('false');
    });

    it('reads object option labels via optionLabel', async () => {
        fixture.componentRef.setInput('optionLabel', 'name');
        const org = { id: 1, name: 'TUM' };
        await search('tu', [org]);
        expect(options()[0].textContent?.trim()).toBe('TUM');
        const selectSpy = vi.spyOn(component.onSelect, 'emit');
        options()[0].click();
        fixture.detectChanges();
        expect(selectSpy).toHaveBeenCalledWith(expect.objectContaining({ value: org }));
        expect(chips()[0].textContent?.trim()).toBe('TUM');
    });

    it('renders chips for an externally written value (CVA writeValue)', () => {
        component.writeValue(['admin', 'artemis']);
        fixture.detectChanges();
        expect(chips().map((c) => c.textContent?.trim())).toEqual(['admin', 'artemis']);
    });

    it('does not open the panel when disabled', async () => {
        fixture.componentRef.setInput('disabled', true);
        fixture.detectChanges();
        expect(input().disabled).toBe(true);
        await search('a', ['admin']);
        expect(listbox()).toBeNull();
    });

    it('suppresses the placeholder once a chip is selected', () => {
        fixture.componentRef.setInput('placeholder', 'Add group');
        fixture.detectChanges();
        expect(input().getAttribute('placeholder')).toBe('Add group');
        component.writeValue(['admin']);
        fixture.detectChanges();
        expect(input().getAttribute('placeholder')).toBeNull();
    });
});

describe('TumUiAutoCompleteComponent with reactive formControl', () => {
    @Component({
        imports: [TumUiAutoCompleteComponent, ReactiveFormsModule],
        template: `<tum-ui-autocomplete [formControl]="control" [multiple]="true" [suggestions]="suggestions" />`,
    })
    class ReactiveHostComponent {
        readonly suggestions = ['b', 'c'];
        readonly control = new FormControl<string[]>(['a']);
    }

    it('reflects the FormControl value as chips and honors control.disable()', async () => {
        await TestBed.configureTestingModule({ imports: [ReactiveHostComponent, FontAwesomeTestingModule] }).compileComponents();
        const fixture = TestBed.createComponent(ReactiveHostComponent);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const chips = () => Array.from(fixture.nativeElement.querySelectorAll('tum-ui-chip')) as HTMLElement[];
        expect(chips().map((c) => c.textContent?.trim())).toEqual(['a']);

        fixture.componentInstance.control.disable();
        fixture.detectChanges();
        const inputEl = fixture.debugElement.query(By.css('input[role="combobox"]')).nativeElement as HTMLInputElement;
        expect(inputEl.disabled).toBe(true);
        fixture.destroy();
    });
});

describe('TumUiAutoCompleteComponent single mode with standalone ngModel', () => {
    @Component({
        imports: [TumUiAutoCompleteComponent, FormsModule],
        template: `<tum-ui-autocomplete [(ngModel)]="value" [suggestions]="suggestions" />`,
    })
    class SingleStandaloneHostComponent {
        value: string | undefined = 'initial';
        readonly suggestions = ['a', 'b'];
    }

    it('renders without throwing when a standalone ngModel writes before view init, then shows the value', async () => {
        await TestBed.configureTestingModule({ imports: [SingleStandaloneHostComponent, FontAwesomeTestingModule] }).compileComponents();
        const fixture = TestBed.createComponent(SingleStandaloneHostComponent);
        expect(() => fixture.detectChanges()).not.toThrow();
        await fixture.whenStable();
        fixture.detectChanges();
        const inputEl = fixture.debugElement.query(By.css('input[role="combobox"]')).nativeElement as HTMLInputElement;
        expect(inputEl.value).toBe('initial');
        fixture.destroy();
    });
});

describe('TumUiAutoCompleteComponent single-mode value + completeOnFocus', () => {
    let fixture: ComponentFixture<TumUiAutoCompleteComponent>;
    let component: TumUiAutoCompleteComponent;
    let input: HTMLInputElement;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [TumUiAutoCompleteComponent, FontAwesomeTestingModule] }).compileComponents();
        fixture = TestBed.createComponent(TumUiAutoCompleteComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('delay', 0);
        fixture.detectChanges();
        input = fixture.debugElement.query(By.css('input[role="combobox"]')).nativeElement as HTMLInputElement;
    });

    afterEach(() => {
        fixture.destroy();
        vi.restoreAllMocks();
    });

    it('single mode: typing mirrors the text through the CVA, and clearing writes undefined', () => {
        const onChange = vi.fn();
        component.registerOnChange(onChange);
        input.value = 'artemis-build-agent-1';
        input.dispatchEvent(new Event('input'));
        expect(onChange).toHaveBeenLastCalledWith('artemis-build-agent-1');
        input.value = '';
        input.dispatchEvent(new Event('input'));
        expect(onChange).toHaveBeenLastCalledWith(undefined);
    });

    it('completeOnFocus: focusing fires completeMethod even with an empty query', () => {
        fixture.componentRef.setInput('completeOnFocus', true);
        fixture.detectChanges();
        const complete = vi.fn();
        component.completeMethod.subscribe(complete);
        const focusEvent = new FocusEvent('focus');
        input.dispatchEvent(focusEvent);
        expect(complete).toHaveBeenCalledWith({ originalEvent: focusEvent, query: '' });
    });
});
