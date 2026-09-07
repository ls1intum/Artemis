import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { DOWN_ARROW, END } from '@angular/cdk/keycodes';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { vi } from 'vitest';
import { TumUiSelectComponent } from './tum-ui-select.component';

interface Option {
    label: string;
    value: string;
}
const OPTIONS: Option[] = [
    { label: 'Alpha', value: 'a' },
    { label: 'Bravo', value: 'b' },
    { label: 'Charlie', value: 'c' },
];

describe('TumUiSelectComponent', () => {
    let component: TumUiSelectComponent;
    let fixture: ComponentFixture<TumUiSelectComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [TumUiSelectComponent, FontAwesomeTestingModule] }).compileComponents();
        fixture = TestBed.createComponent(TumUiSelectComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('options', OPTIONS);
        fixture.componentRef.setInput('optionLabel', 'label');
        fixture.componentRef.setInput('optionValue', 'value');
        fixture.detectChanges();
    });

    afterEach(() => {
        fixture.destroy();
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    function triggerButton(): HTMLButtonElement {
        return fixture.debugElement.query(By.css('button[aria-haspopup="listbox"]')).nativeElement;
    }
    function labelText(): string {
        return triggerButton().textContent?.trim() ?? '';
    }
    function openPanel(): void {
        triggerButton().click();
        fixture.detectChanges();
    }
    function listbox(): HTMLElement {
        return document.querySelector('[role="listbox"]') as HTMLElement;
    }
    function optionElements(): HTMLElement[] {
        return Array.from(document.querySelectorAll('[role="option"]')) as HTMLElement[];
    }

    it('renders the placeholder when nothing is selected', () => {
        fixture.componentRef.setInput('placeholder', 'Pick one');
        fixture.detectChanges();
        expect(labelText()).toBe('Pick one');
    });

    it('reflects an externally written value as the matching option label (CVA writeValue)', () => {
        component.writeValue('b');
        fixture.detectChanges();
        expect(labelText()).toBe('Bravo');
    });

    it('opens a role="listbox" panel with one role="option" per option and marks the selected one', () => {
        component.writeValue('c');
        fixture.detectChanges();
        openPanel();
        expect(listbox()).not.toBeNull();
        expect(listbox().getAttribute('role')).toBe('listbox');
        const options = optionElements();
        expect(options).toHaveLength(3);
        expect(options.map((o) => o.getAttribute('role'))).toEqual(['option', 'option', 'option']);
        expect(options[2].getAttribute('aria-selected')).toBe('true');
        expect(options[0].getAttribute('aria-selected')).toBe('false');
        expect(triggerButton().getAttribute('aria-expanded')).toBe('true');
    });

    it('scrolls the selected option into view when opening', () => {
        const scrollIntoView = vi.fn();
        const original = HTMLElement.prototype.scrollIntoView;
        Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', { configurable: true, value: scrollIntoView });
        try {
            component.writeValue('c');
            fixture.detectChanges();
            openPanel();
            expect(scrollIntoView).toHaveBeenCalledWith({ block: 'nearest' });
            expect(scrollIntoView.mock.instances).toEqual([optionElements()[2]]);
        } finally {
            Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', { configurable: true, value: original });
        }
    });

    it('selecting an option updates the CVA, emits selectionChange, and closes', () => {
        const onChangeCallback = vi.fn();
        component.registerOnChange(onChangeCallback);
        const emitSpy = vi.spyOn(component.selectionChange, 'emit');
        triggerButton().focus();
        openPanel();
        optionElements()[1].click();
        fixture.detectChanges();
        expect(onChangeCallback).toHaveBeenCalledWith('b');
        expect(emitSpy).toHaveBeenCalledWith('b');
        expect(labelText()).toBe('Bravo');
        expect(listbox()).toBeNull();
        expect(document.activeElement).toBe(triggerButton());
    });

    it('emits the whole option object when optionValue is not set', () => {
        fixture.componentRef.setInput('optionValue', undefined);
        fixture.detectChanges();
        const emitSpy = vi.spyOn(component.selectionChange, 'emit');
        openPanel();
        optionElements()[0].click();
        fixture.detectChanges();
        expect(emitSpy).toHaveBeenCalledWith(OPTIONS[0]);
        expect(labelText()).toBe('Alpha');
    });

    it('keyboard: ArrowDown advances the active option and Enter selects it', () => {
        const emitSpy = vi.spyOn(component.selectionChange, 'emit');
        openPanel();
        triggerButton().dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', keyCode: DOWN_ARROW, bubbles: true }));
        fixture.detectChanges();
        expect(triggerButton().getAttribute('aria-activedescendant')).toBe(optionElements()[1].id);
        triggerButton().dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
        fixture.detectChanges();
        expect(emitSpy).toHaveBeenCalledWith('b');
    });

    it('keyboard: ArrowDown stops at the final option', () => {
        openPanel();
        triggerButton().dispatchEvent(new KeyboardEvent('keydown', { key: 'End', keyCode: END, bubbles: true }));
        triggerButton().dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', keyCode: DOWN_ARROW, bubbles: true }));
        fixture.detectChanges();
        expect(triggerButton().getAttribute('aria-activedescendant')).toBe(optionElements()[2].id);
    });

    it.each(['Enter', ' '])('keyboard: %j opens the closed select', (key) => {
        const event = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true });
        triggerButton().dispatchEvent(event);
        fixture.detectChanges();
        expect(event.defaultPrevented).toBe(true);
        expect(listbox()).not.toBeNull();
    });

    it('keyboard: Escape closes the panel without selecting', () => {
        const emitSpy = vi.spyOn(component.selectionChange, 'emit');
        openPanel();
        triggerButton().dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
        fixture.detectChanges();
        expect(listbox()).toBeNull();
        expect(emitSpy).not.toHaveBeenCalled();
    });

    it('type-ahead activates the first option whose label starts with the typed character', () => {
        vi.useFakeTimers();
        openPanel();
        triggerButton().dispatchEvent(new KeyboardEvent('keydown', { key: 'c', bubbles: true }));
        vi.advanceTimersByTime(500);
        fixture.detectChanges();
        expect(triggerButton().getAttribute('aria-activedescendant')).toBe(optionElements()[2].id);
    });

    it('discards pending type-ahead input when the panel closes', () => {
        vi.useFakeTimers();
        openPanel();
        triggerButton().dispatchEvent(new KeyboardEvent('keydown', { key: 'c', bubbles: true }));
        triggerButton().dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
        triggerButton().click();
        vi.advanceTimersByTime(500);
        fixture.detectChanges();
        expect(triggerButton().getAttribute('aria-activedescendant')).toBe(optionElements()[0].id);
    });

    it('cycles through matching options when the same type-ahead character is repeated', () => {
        vi.useFakeTimers();
        fixture.componentRef.setInput('options', [
            { label: 'Alpha', value: 'a' },
            { label: 'Alpine', value: 'alpine' },
            { label: 'Bravo', value: 'b' },
        ]);
        openPanel();
        triggerButton().dispatchEvent(new KeyboardEvent('keydown', { key: 'a', bubbles: true }));
        triggerButton().dispatchEvent(new KeyboardEvent('keydown', { key: 'a', bubbles: true }));
        vi.advanceTimersByTime(500);
        fixture.detectChanges();
        expect(triggerButton().getAttribute('aria-activedescendant')).toBe(optionElements()[1].id);
    });

    it('commits the active option on Tab without preventing focus navigation', () => {
        const emitSpy = vi.spyOn(component.selectionChange, 'emit');
        openPanel();
        triggerButton().dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', keyCode: DOWN_ARROW, bubbles: true }));
        const event = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true });
        triggerButton().dispatchEvent(event);
        fixture.detectChanges();
        expect(emitSpy).toHaveBeenCalledWith('b');
        expect(event.defaultPrevented).toBe(false);
    });

    it('keeps aria-activedescendant valid when options shrink', () => {
        openPanel();
        triggerButton().dispatchEvent(new KeyboardEvent('keydown', { key: 'End', keyCode: END, bubbles: true }));
        fixture.detectChanges();
        fixture.componentRef.setInput('options', OPTIONS.slice(0, 2));
        fixture.detectChanges();
        expect(triggerButton().getAttribute('aria-activedescendant')).toBe(optionElements()[1].id);
    });

    it('activates the first option when an open empty select receives options', () => {
        fixture.componentRef.setInput('options', []);
        fixture.detectChanges();
        openPanel();
        fixture.componentRef.setInput('options', OPTIONS);
        fixture.detectChanges();
        expect(triggerButton().getAttribute('aria-activedescendant')).toBe(optionElements()[0].id);
    });

    it('does not open when disabled and reflects the disabled attribute on the trigger', () => {
        fixture.componentRef.setInput('disabled', true);
        fixture.detectChanges();
        expect(triggerButton().disabled).toBe(true);
        triggerButton().click();
        fixture.detectChanges();
        expect(listbox()).toBeNull();
    });

    it('setDisabledState(true) closes an open panel (reactive-forms disable while open)', () => {
        openPanel();
        expect(listbox()).not.toBeNull();
        component.setDisabledState(true);
        fixture.detectChanges();
        expect(listbox()).toBeNull();
    });

    it('showClear renders a clear button that resets the value and emits undefined', () => {
        fixture.componentRef.setInput('showClear', true);
        const onChangeCallback = vi.fn();
        component.registerOnChange(onChangeCallback);
        component.writeValue('a');
        fixture.detectChanges();
        const clear = fixture.debugElement.query(By.css('button:not([aria-haspopup])')).nativeElement as HTMLButtonElement;
        clear.focus();
        clear.click();
        fixture.detectChanges();
        expect(onChangeCallback).toHaveBeenCalledWith(undefined);
        expect(document.activeElement).toBe(triggerButton());
    });

    it('renders the empty message when there are no options', () => {
        fixture.componentRef.setInput('options', []);
        fixture.componentRef.setInput('emptyMessage', 'Nothing here');
        fixture.detectChanges();
        openPanel();
        const emptyOption = optionElements()[0];
        expect(emptyOption.textContent?.trim()).toBe('Nothing here');
        expect(emptyOption.getAttribute('aria-disabled')).toBe('true');
        expect(emptyOption.getAttribute('aria-selected')).toBe('false');
    });
    describe('filter', () => {
        function filterField(): HTMLInputElement | null {
            return document.querySelector('.tum-ui-select-filter');
        }
        function requireFilterField(): HTMLInputElement {
            const field = filterField();
            expect(field).not.toBeNull();
            return field!;
        }
        function type(query: string): void {
            const field = requireFilterField();
            field.value = query;
            field.dispatchEvent(new Event('input'));
            fixture.detectChanges();
        }

        beforeEach(() => {
            fixture.componentRef.setInput('filter', true);
            fixture.detectChanges();
        });

        it('shows no search field unless asked for one', () => {
            fixture.componentRef.setInput('filter', false);
            fixture.detectChanges();
            openPanel();

            expect(filterField()).toBeNull();
        });

        it('still reports an empty option set as empty when filtering is off', () => {
            // The message follows whether a query is narrowing the list, not merely whether one was typed, so
            // turning the filter off cannot leave the list claiming that nothing matched.
            openPanel();
            type('zzz');
            fixture.componentRef.setInput('filter', false);
            fixture.componentRef.setInput('options', []);
            fixture.detectChanges();

            expect(optionElements()[0].textContent?.trim()).toBe('No available options');
        });

        it('narrows the list to the options whose label matches, case-insensitively', () => {
            openPanel();

            type('ra');

            expect(optionElements().map((option) => option.textContent?.trim())).toEqual(['Bravo']);
        });

        it('searches the named fields instead of the label when filterBy is given', () => {
            fixture.componentRef.setInput('filterBy', 'value');
            fixture.detectChanges();
            openPanel();

            type('c');

            // 'c' is the value of Charlie; matching on the label would also have kept Bravo, which contains no c
            // in its value.
            expect(optionElements().map((option) => option.textContent?.trim())).toEqual(['Charlie']);
        });

        it('reports that nothing matched rather than that there are no options', () => {
            openPanel();

            type('zzz');

            expect(optionElements()).toHaveLength(1);
            expect(optionElements()[0].textContent?.trim()).toBe('No matching options');
            expect(optionElements()[0].getAttribute('aria-disabled')).toBe('true');
        });

        it('commits the option at the active index of the narrowed list, not of the full one', () => {
            openPanel();

            // 'ar' leaves only Charlie, which is index 2 in the full list and index 0 in the narrowed one.
            type('ar');
            requireFilterField().dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
            fixture.detectChanges();

            expect(labelText()).toBe('Charlie');
        });

        it('walks the narrowed list with the arrow keys from the search field', () => {
            openPanel();

            // 'a' keeps Alpha, Bravo and Charlie, so there is somewhere to move to.
            type('a');
            requireFilterField().dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', keyCode: DOWN_ARROW, bubbles: true }));
            fixture.detectChanges();
            requireFilterField().dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
            fixture.detectChanges();

            expect(labelText()).toBe('Bravo');
        });

        it('describes the active option on the search field, because focus is there while filtering', () => {
            openPanel();
            type('a');

            const activeId = requireFilterField().getAttribute('aria-activedescendant');
            expect(activeId).toBeTruthy();
            expect(document.getElementById(activeId!)?.getAttribute('role')).toBe('option');
        });

        it('forgets the query once the panel closes, so the next open starts from the whole list', () => {
            openPanel();
            type('ra');
            expect(optionElements()).toHaveLength(1);

            requireFilterField().dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
            fixture.detectChanges();
            openPanel();

            expect(optionElements()).toHaveLength(OPTIONS.length);
            expect(requireFilterField().value).toBe('');
        });
    });
});

describe('TumUiSelectComponent with [(ngModel)]', () => {
    @Component({
        imports: [TumUiSelectComponent, FormsModule],
        template: `<tum-ui-select [(ngModel)]="value" [options]="options" optionLabel="label" optionValue="value" />`,
    })
    class NgModelHostComponent {
        readonly options = OPTIONS;
        value = signal<string | undefined>('a');
    }

    it('two-way binds the primitive value through ngModel', async () => {
        await TestBed.configureTestingModule({ imports: [NgModelHostComponent, FontAwesomeTestingModule] }).compileComponents();
        const fixture = TestBed.createComponent(NgModelHostComponent);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const trigger = fixture.debugElement.query(By.css('button[aria-haspopup="listbox"]')).nativeElement as HTMLButtonElement;
        expect(trigger.textContent?.trim()).toBe('Alpha');

        trigger.click();
        fixture.detectChanges();
        (document.querySelectorAll('[role="option"]')[2] as HTMLElement).click();
        fixture.detectChanges();
        await fixture.whenStable();
        expect(fixture.componentInstance.value()).toBe('c');
        fixture.destroy();
    });
});

describe('TumUiSelectComponent with reactive formControl', () => {
    @Component({
        imports: [TumUiSelectComponent, ReactiveFormsModule],
        template: `<tum-ui-select [formControl]="control" [options]="options" optionLabel="label" optionValue="value" />`,
    })
    class ReactiveHostComponent {
        readonly options = OPTIONS;
        readonly control = new FormControl<string | undefined>('b');
    }

    it('reflects and updates the FormControl value, and honors control.disable()', async () => {
        await TestBed.configureTestingModule({ imports: [ReactiveHostComponent, FontAwesomeTestingModule] }).compileComponents();
        const fixture = TestBed.createComponent(ReactiveHostComponent);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const trigger = fixture.debugElement.query(By.css('button[aria-haspopup="listbox"]')).nativeElement as HTMLButtonElement;
        expect(trigger.textContent?.trim()).toBe('Bravo');

        trigger.click();
        fixture.detectChanges();
        (document.querySelectorAll('[role="option"]')[0] as HTMLElement).click();
        fixture.detectChanges();
        expect(fixture.componentInstance.control.value).toBe('a');

        fixture.componentInstance.control.disable();
        fixture.detectChanges();
        expect(trigger.disabled).toBe(true);
        fixture.destroy();
    });
});
