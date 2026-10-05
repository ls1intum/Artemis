import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { DOWN_ARROW, END, HOME, UP_ARROW } from '@angular/cdk/keycodes';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { vi } from 'vitest';
import { TumAetUiMultiSelectComponent } from './tumaet-ui-multi-select.component';

interface Option {
    label: string;
    value: string;
}
const OPTIONS: Option[] = [
    { label: 'Alpha', value: 'a' },
    { label: 'Bravo', value: 'b' },
    { label: 'Charlie', value: 'c' },
    { label: 'Delta', value: 'd' },
];

describe('TumAetUiMultiSelectComponent', () => {
    let component: TumAetUiMultiSelectComponent;
    let fixture: ComponentFixture<TumAetUiMultiSelectComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [TumAetUiMultiSelectComponent, FontAwesomeTestingModule] }).compileComponents();
        fixture = TestBed.createComponent(TumAetUiMultiSelectComponent);
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

    function trigger(): HTMLElement {
        return fixture.debugElement.query(By.css('[role="combobox"]')).nativeElement;
    }
    function triggerText(): string {
        return trigger().textContent?.trim() ?? '';
    }
    function openPanel(): void {
        trigger().click();
        fixture.detectChanges();
    }
    function options(): HTMLElement[] {
        return Array.from(document.querySelectorAll('[role="option"]')) as HTMLElement[];
    }
    const KEY_CODES: Record<string, number> = { ArrowDown: DOWN_ARROW, ArrowUp: UP_ARROW, Home: HOME, End: END };
    function press(key: string): void {
        // The CDK key manager reads `keyCode`, as real browsers provide it.
        trigger().dispatchEvent(new KeyboardEvent('keydown', { key, keyCode: KEY_CODES[key], bubbles: true, cancelable: true }));
        fixture.detectChanges();
    }

    it('renders the placeholder when nothing is chosen', () => {
        fixture.componentRef.setInput('placeholder', 'Pick some');
        fixture.detectChanges();
        expect(triggerText()).toBe('Pick some');
    });

    it('lists the labels of the written values in the order of the options', () => {
        component.writeValue(['c', 'a']);
        fixture.detectChanges();
        expect(triggerText()).toBe('Alpha, Charlie');
    });

    it('falls back to a count once more than maxSelectedLabels are chosen', () => {
        fixture.componentRef.setInput('maxSelectedLabels', 2);
        component.writeValue(['a', 'b', 'c']);
        fixture.detectChanges();
        expect(triggerText()).toBe('3 selected');
    });

    it('opens a multiselectable listbox with one option per entry and marks the chosen ones', () => {
        component.writeValue(['b']);
        fixture.detectChanges();
        openPanel();
        expect(document.querySelector('[role="listbox"]')?.getAttribute('aria-multiselectable')).toBe('true');
        expect(options().map((option) => option.getAttribute('aria-selected'))).toEqual(['false', 'true', 'false', 'false']);
        expect(trigger().getAttribute('aria-expanded')).toBe('true');
    });

    it('toggles options without closing the panel and reports every change', () => {
        const onChange = vi.fn();
        const emitted = vi.fn();
        component.registerOnChange(onChange);
        component.selectionChange.subscribe(emitted);
        openPanel();

        options()[0].click();
        fixture.detectChanges();
        options()[2].click();
        fixture.detectChanges();

        expect(onChange).toHaveBeenLastCalledWith(['a', 'c']);
        expect(emitted).toHaveBeenLastCalledWith(['a', 'c']);
        expect(trigger().getAttribute('aria-expanded')).toBe('true');
        expect(triggerText()).toBe('Alpha, Charlie');

        options()[0].click();
        fixture.detectChanges();
        expect(onChange).toHaveBeenLastCalledWith(['c']);
        expect(options()[0].getAttribute('aria-selected')).toBe('false');
    });

    it('writes the options themselves when no optionValue is given', () => {
        fixture.componentRef.setInput('optionValue', undefined);
        const onChange = vi.fn();
        component.registerOnChange(onChange);
        openPanel();
        options()[1].click();
        expect(onChange).toHaveBeenCalledWith([OPTIONS[1]]);
    });

    it('opens with ArrowDown, toggles the active option with Enter and Space, and closes with Escape', () => {
        const onChange = vi.fn();
        component.registerOnChange(onChange);

        press('ArrowDown');
        expect(trigger().getAttribute('aria-expanded')).toBe('true');
        press('ArrowDown');
        press('Enter');
        expect(onChange).toHaveBeenLastCalledWith(['b']);
        press(' ');
        expect(onChange).toHaveBeenLastCalledWith([]);

        press('Escape');
        expect(trigger().getAttribute('aria-expanded')).toBe('false');
    });

    it('closes without choosing when Tab is pressed', () => {
        const onChange = vi.fn();
        component.registerOnChange(onChange);
        openPanel();
        press('Tab');
        expect(trigger().getAttribute('aria-expanded')).toBe('false');
        expect(onChange).not.toHaveBeenCalled();
    });

    it('does not open when disabled', () => {
        fixture.componentRef.setInput('disabled', true);
        fixture.detectChanges();
        openPanel();
        expect(trigger().getAttribute('aria-expanded')).toBe('false');
        expect(trigger().getAttribute('aria-disabled')).toBe('true');
        expect(trigger().getAttribute('tabindex')).toBe('-1');
    });

    it('shows the empty message when there are no options', () => {
        fixture.componentRef.setInput('options', []);
        fixture.componentRef.setInput('emptyMessage', 'Nothing here');
        fixture.detectChanges();
        openPanel();
        expect(options().map((option) => option.textContent?.trim())).toEqual(['Nothing here']);
    });

    it('renders the outlined variant like an outlined primary button', () => {
        expect(trigger().className).not.toContain('tumaet:bg-transparent');
        fixture.componentRef.setInput('variant', 'outlined');
        fixture.detectChanges();
        expect(trigger().className).toContain('tumaet:bg-transparent');
        expect(trigger().className).toContain('tumaet:border-primary');
        expect(trigger().className).toContain('tumaet:text-accent');
    });

    it('tells assistive technology that the control is required', () => {
        fixture.componentRef.setInput('required', true);
        fixture.detectChanges();
        expect(trigger().getAttribute('aria-required')).toBe('true');
    });
});

@Component({
    imports: [TumAetUiMultiSelectComponent, ReactiveFormsModule],
    template: `
        <tumaet-ui-multi-select [options]="options" optionLabel="label" optionValue="value" [formControl]="control" ariaLabel="Letters">
            <ng-template #option let-option let-selected="selected">
                <span data-testid="custom-option" [attr.data-selected]="selected">{{ option.label }}!</span>
            </ng-template>
            <ng-template #selectedItems let-chosen>
                <span data-testid="custom-summary">{{ chosen.length }} chosen</span>
                <button type="button" data-testid="custom-remove" (click)="remove($event)">x</button>
            </ng-template>
        </tumaet-ui-multi-select>
    `,
})
class TemplateHostComponent {
    readonly options = OPTIONS;
    readonly control = new FormControl<string[]>(['a', 'b']);
    readonly removed = signal(0);

    remove(event: Event): void {
        event.stopPropagation();
        this.removed.update((count) => count + 1);
    }
}

describe('TumAetUiMultiSelectComponent templates and forms', () => {
    let fixture: ComponentFixture<TemplateHostComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [TemplateHostComponent, FontAwesomeTestingModule] }).compileComponents();
        fixture = TestBed.createComponent(TemplateHostComponent);
        fixture.detectChanges();
    });

    afterEach(() => fixture.destroy());

    function trigger(): HTMLElement {
        return fixture.debugElement.query(By.css('[role="combobox"]')).nativeElement;
    }

    it('renders the selectedItems template inside the trigger with the chosen options', () => {
        expect(trigger().querySelector('[data-testid="custom-summary"]')?.textContent?.trim()).toBe('2 chosen');
    });

    it('lets interactive content of the selectedItems template work without toggling the panel', () => {
        const host = fixture.componentInstance;
        (trigger().querySelector('[data-testid="custom-remove"]') as HTMLElement).click();
        fixture.detectChanges();
        expect(host.removed()).toBe(1);
        expect(trigger().getAttribute('aria-expanded')).toBe('false');
    });

    it('renders the option template with the selected state', () => {
        trigger().click();
        fixture.detectChanges();
        const rows = Array.from(document.querySelectorAll('[data-testid="custom-option"]')) as HTMLElement[];
        expect(rows.map((row) => row.textContent?.trim())).toEqual(['Alpha!', 'Bravo!', 'Charlie!', 'Delta!']);
        expect(rows.map((row) => row.getAttribute('data-selected'))).toEqual(['true', 'true', 'false', 'false']);
    });

    it('writes the chosen values to the form control and reflects control changes', () => {
        const host = fixture.componentInstance;
        trigger().click();
        fixture.detectChanges();
        (document.querySelectorAll('[role="option"]')[3] as HTMLElement).click();
        fixture.detectChanges();
        expect(host.control.value).toEqual(['a', 'b', 'd']);

        host.control.setValue(['c']);
        fixture.detectChanges();
        expect(trigger().querySelector('[data-testid="custom-summary"]')?.textContent?.trim()).toBe('1 chosen');
    });

    it('disables the control with the form control', () => {
        fixture.componentInstance.control.disable();
        fixture.detectChanges();
        trigger().click();
        fixture.detectChanges();
        expect(trigger().getAttribute('aria-expanded')).toBe('false');
        expect(trigger().getAttribute('aria-disabled')).toBe('true');
    });
});
