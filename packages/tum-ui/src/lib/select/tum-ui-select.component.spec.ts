import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
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
        vi.restoreAllMocks();
    });

    function triggerButton(): HTMLButtonElement {
        return fixture.debugElement.query(By.css('[data-testid="tum-ui-select-trigger"]')).nativeElement;
    }
    function labelText(): string {
        return fixture.debugElement.query(By.css('[data-testid="tum-ui-select-label"]')).nativeElement.textContent.trim();
    }
    function openPanel(): void {
        triggerButton().click();
        fixture.detectChanges();
    }
    function listbox(): HTMLElement {
        return document.querySelector('[data-testid="tum-ui-select-listbox"]') as HTMLElement;
    }
    function optionElements(): HTMLElement[] {
        return Array.from(document.querySelectorAll('[data-testid="tum-ui-select-option"]')) as HTMLElement[];
    }

    it('renders the placeholder when nothing is selected', () => {
        fixture.componentRef.setInput('placeholder', 'Pick one');
        fixture.detectChanges();
        expect(labelText()).toBe('Pick one');
        expect(component['hasSelection']()).toBe(false);
    });

    it('reflects an externally written value as the matching option label (CVA writeValue)', () => {
        component.writeValue('b');
        fixture.detectChanges();
        expect(labelText()).toBe('Bravo');
        expect(component['hasSelection']()).toBe(true);
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

    it('selecting an option pushes the resolved optionValue through the CVA and emits onChange, then closes', () => {
        const onChangeCallback = vi.fn();
        component.registerOnChange(onChangeCallback);
        const emitSpy = vi.spyOn(component.onChange, 'emit');
        openPanel();
        optionElements()[1].click();
        fixture.detectChanges();
        expect(onChangeCallback).toHaveBeenCalledWith('b');
        expect(emitSpy).toHaveBeenCalledWith('b');
        expect(labelText()).toBe('Bravo');
        expect(listbox()).toBeNull(); // closed after selection
    });

    it('emits the whole option object when optionValue is not set', () => {
        fixture.componentRef.setInput('optionValue', undefined);
        fixture.detectChanges();
        const emitSpy = vi.spyOn(component.onChange, 'emit');
        openPanel();
        optionElements()[0].click();
        fixture.detectChanges();
        expect(emitSpy).toHaveBeenCalledWith(OPTIONS[0]);
        expect(labelText()).toBe('Alpha');
    });

    it('keyboard: ArrowDown advances the active option and Enter selects it', () => {
        const emitSpy = vi.spyOn(component.onChange, 'emit');
        openPanel();
        listbox().dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }));
        fixture.detectChanges();
        expect(component['activeIndex']()).toBe(1);
        expect(listbox().getAttribute('aria-activedescendant')).toBe(optionElements()[1].id);
        listbox().dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
        fixture.detectChanges();
        expect(emitSpy).toHaveBeenCalledWith('b');
    });

    it('keyboard: Escape closes the panel without selecting', () => {
        const emitSpy = vi.spyOn(component.onChange, 'emit');
        openPanel();
        listbox().dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
        fixture.detectChanges();
        expect(listbox()).toBeNull();
        expect(emitSpy).not.toHaveBeenCalled();
    });

    it('type-ahead activates the first option whose label starts with the typed character', () => {
        openPanel();
        listbox().dispatchEvent(new KeyboardEvent('keydown', { key: 'c', bubbles: true }));
        fixture.detectChanges();
        expect(component['activeIndex']()).toBe(2); // Charlie
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
        const clear = fixture.debugElement.query(By.css('[data-testid="tum-ui-select-clear"]')).nativeElement as HTMLButtonElement;
        clear.click();
        fixture.detectChanges();
        expect(onChangeCallback).toHaveBeenCalledWith(undefined);
        expect(component['hasSelection']()).toBe(false);
    });

    it('renders the empty message when there are no options', () => {
        fixture.componentRef.setInput('options', []);
        fixture.componentRef.setInput('emptyMessage', 'Nothing here');
        fixture.detectChanges();
        openPanel();
        expect(optionElements()).toHaveLength(0);
        expect((document.querySelector('[data-testid="tum-ui-select-empty"]') as HTMLElement).textContent?.trim()).toBe('Nothing here');
    });

    it('forwards styleClass onto the host and sizing to the trigger', () => {
        fixture.componentRef.setInput('styleClass', 'w-full');
        fixture.componentRef.setInput('size', 'small');
        fixture.detectChanges();
        expect((fixture.nativeElement as HTMLElement).classList.contains('w-full')).toBe(true);
        expect(triggerButton().className).toContain('text-sm');
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

        expect(fixture.debugElement.query(By.css('[data-testid="tum-ui-select-label"]')).nativeElement.textContent.trim()).toBe('Alpha');

        fixture.debugElement.query(By.css('[data-testid="tum-ui-select-trigger"]')).nativeElement.click();
        fixture.detectChanges();
        (document.querySelectorAll('[data-testid="tum-ui-select-option"]')[2] as HTMLElement).click();
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

        expect(fixture.debugElement.query(By.css('[data-testid="tum-ui-select-label"]')).nativeElement.textContent.trim()).toBe('Bravo');

        fixture.debugElement.query(By.css('[data-testid="tum-ui-select-trigger"]')).nativeElement.click();
        fixture.detectChanges();
        (document.querySelectorAll('[data-testid="tum-ui-select-option"]')[0] as HTMLElement).click();
        fixture.detectChanges();
        expect(fixture.componentInstance.control.value).toBe('a');

        fixture.componentInstance.control.disable();
        fixture.detectChanges();
        expect((fixture.debugElement.query(By.css('[data-testid="tum-ui-select-trigger"]')).nativeElement as HTMLButtonElement).disabled).toBe(true);
        fixture.destroy();
    });
});
