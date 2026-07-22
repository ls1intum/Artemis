import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { vi } from 'vitest';
import { TumUiAutoCompleteComponent } from 'app/shared-ui/tum-ui/autocomplete/tum-ui-autocomplete.component';

// Overlay geometry / real pointer capture are not headless-verifiable, so these specs assert the wiring,
// the ARIA semantics, the CVA value flow, and the emitted event payloads — never pixel positions (see the kit
// README). The suggestions panel is portaled to the CDK overlay container, so it is queried off `document`.
describe('TumUiAutoCompleteComponent (multiple mode)', () => {
    let component: TumUiAutoCompleteComponent;
    let fixture: ComponentFixture<TumUiAutoCompleteComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [TumUiAutoCompleteComponent, FontAwesomeTestingModule] }).compileComponents();
        fixture = TestBed.createComponent(TumUiAutoCompleteComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('multiple', true);
        fixture.componentRef.setInput('delay', 0); // debounce isolated in its own fake-timer test; 0 elsewhere
        fixture.detectChanges();
    });

    afterEach(() => {
        fixture.destroy();
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    const flush = () => new Promise((resolve) => setTimeout(resolve, 0));

    function input(): HTMLInputElement {
        return fixture.debugElement.query(By.css('[data-testid="tum-ui-autocomplete-input"]')).nativeElement;
    }
    function typeQuery(text: string): void {
        const el = input();
        el.value = text;
        el.dispatchEvent(new Event('input', { bubbles: true }));
    }
    function listbox(): HTMLElement | null {
        return document.querySelector('[data-testid="tum-ui-autocomplete-listbox"]');
    }
    function options(): HTMLElement[] {
        return Array.from(document.querySelectorAll('[data-testid="tum-ui-autocomplete-option"]'));
    }
    function chips(): HTMLElement[] {
        return Array.from(fixture.nativeElement.querySelectorAll('[data-testid="tum-ui-autocomplete-chip"]'));
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
        typeQuery('ad'); // resets the debounce timer
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
        expect(listbox()).toBeNull(); // closed + input cleared after selection
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
        const removeButton = chips()[0].querySelector('[data-testid="tum-ui-chip-remove"]') as HTMLButtonElement;
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
        expect(component['activeIndex']()).toBe(0);
        expect(input().getAttribute('aria-activedescendant')).toBe(options()[0].id);
        input().dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
        fixture.detectChanges();
        expect(selectSpy).toHaveBeenCalledWith(expect.objectContaining({ value: 'admin' }));
    });

    it('shows the empty message when a search returns no suggestions', async () => {
        fixture.componentRef.setInput('emptyMessage', 'Nothing found');
        await search('zzz', []);
        expect(options()).toHaveLength(0);
        expect((document.querySelector('[data-testid="tum-ui-autocomplete-empty"]') as HTMLElement).textContent?.trim()).toBe('Nothing found');
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

        const chips = () => Array.from(fixture.nativeElement.querySelectorAll('[data-testid="tum-ui-autocomplete-chip"]')) as HTMLElement[];
        expect(chips().map((c) => c.textContent?.trim())).toEqual(['a']);

        fixture.componentInstance.control.disable();
        fixture.detectChanges();
        const inputEl = fixture.debugElement.query(By.css('[data-testid="tum-ui-autocomplete-input"]')).nativeElement as HTMLInputElement;
        expect(inputEl.disabled).toBe(true);
        fixture.destroy();
    });
});

describe('TumUiAutoCompleteComponent single mode with standalone ngModel', () => {
    @Component({
        imports: [TumUiAutoCompleteComponent, FormsModule],
        // No <form> and no name → Angular treats this as a standalone NgModel, which calls writeValue()
        // synchronously during the creation-pass ngOnChanges (before the child view exists). Regression guard
        // for the NG0951 crash when writeValue → syncSingleInputText read a `viewChild.required('textInput')`.
        template: `<tum-ui-autocomplete [(ngModel)]="value" [suggestions]="suggestions" />`,
    })
    class SingleStandaloneHostComponent {
        value: string | undefined = 'initial';
        readonly suggestions = ['a', 'b'];
    }

    it('renders without throwing when a standalone ngModel writes before view init, then shows the value', async () => {
        await TestBed.configureTestingModule({ imports: [SingleStandaloneHostComponent, FontAwesomeTestingModule] }).compileComponents();
        const fixture = TestBed.createComponent(SingleStandaloneHostComponent);
        // The standalone ngModel's synchronous pre-view-init writeValue must not throw (the NG0951 regression).
        expect(() => fixture.detectChanges()).not.toThrow();
        // Once CD settles, the sync effect re-runs (the textInput view-query has resolved), so the initial value
        // displays — proving the fix restores value display, not just suppresses the crash.
        await fixture.whenStable();
        fixture.detectChanges();
        const inputEl = fixture.debugElement.query(By.css('[data-testid="tum-ui-autocomplete-input"]')).nativeElement as HTMLInputElement;
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
        fixture.componentRef.setInput('delay', 0); // single mode is the default (multiple defaults to false)
        fixture.detectChanges();
        input = fixture.debugElement.query(By.css('[data-testid="tum-ui-autocomplete-input"]')).nativeElement as HTMLInputElement;
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
        input.dispatchEvent(new Event('focus'));
        expect(complete).toHaveBeenCalledWith(expect.objectContaining({ query: '' }));
    });
});
