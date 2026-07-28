import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { Component } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TumUiSelectButtonComponent } from './tum-ui-select-button.component';

interface SpanOption {
    label: string;
    value: string;
}

const OPTIONS: SpanOption[] = [
    { label: 'Day', value: 'DAY' },
    { label: 'Week', value: 'WEEK' },
    { label: 'Month', value: 'MONTH' },
];

describe('TumUiSelectButtonComponent', () => {
    let fixture: ComponentFixture<TumUiSelectButtonComponent>;
    let component: TumUiSelectButtonComponent;
    let host: HTMLElement;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TumUiSelectButtonComponent],
        }).compileComponents();
        fixture = TestBed.createComponent(TumUiSelectButtonComponent);
        component = fixture.componentInstance;
        host = fixture.nativeElement as HTMLElement;
        fixture.componentRef.setInput('options', OPTIONS);
        fixture.componentRef.setInput('optionLabel', 'label');
        fixture.componentRef.setInput('optionValue', 'value');
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    function buttons(): HTMLButtonElement[] {
        return Array.from(host.querySelectorAll('button'));
    }

    function arrow(from: number, key: string): void {
        buttons()[from].dispatchEvent(new KeyboardEvent('keydown', { key, cancelable: true, bubbles: true }));
        fixture.detectChanges();
    }

    it('exposes the radiogroup / radio roles and renders one option per entry with its label', () => {
        expect(host.getAttribute('role')).toBe('radiogroup');
        const rendered = buttons();
        expect(rendered).toHaveLength(3);
        expect(rendered.map((b) => b.getAttribute('role'))).toEqual(['radio', 'radio', 'radio']);
        expect(rendered.map((b) => b.textContent?.trim())).toEqual(['Day', 'Week', 'Month']);
        expect(rendered.map((b) => b.getAttribute('tabindex'))).toEqual(['0', '-1', '-1']);
    });

    it('selects on click, updates the CVA value, and emits', () => {
        const changed = vi.fn();
        const onChange = vi.fn();
        component.changed.subscribe(changed);
        component.registerOnChange(onChange);

        buttons()[1].click();
        fixture.detectChanges();

        expect(buttons().map((b) => b.getAttribute('aria-checked'))).toEqual(['false', 'true', 'false']);
        expect(changed).toHaveBeenCalledWith('WEEK');
        expect(onChange).toHaveBeenCalledWith('WEEK');
        expect(buttons().map((b) => b.getAttribute('tabindex'))).toEqual(['-1', '0', '-1']);
    });

    it('reflects the value written through the ControlValueAccessor', () => {
        component.writeValue('MONTH');
        fixture.detectChanges();
        expect(buttons()[2].getAttribute('aria-checked')).toBe('true');
    });

    it('clears the selection when the selected option is re-clicked (allowEmpty default true)', () => {
        const changed = vi.fn();
        component.changed.subscribe(changed);
        component.writeValue('WEEK');
        fixture.detectChanges();

        buttons()[1].click();
        fixture.detectChanges();

        expect(buttons().every((b) => b.getAttribute('aria-checked') === 'false')).toBe(true);
        expect(changed).toHaveBeenCalledWith(undefined);
    });

    it('keeps the selection when re-clicked with allowEmpty=false', () => {
        fixture.componentRef.setInput('allowEmpty', false);
        component.writeValue('WEEK');
        fixture.detectChanges();

        buttons()[1].click();
        fixture.detectChanges();

        expect(buttons()[1].getAttribute('aria-checked')).toBe('true');
    });

    it('roams and selects with the arrow keys', () => {
        component.writeValue('DAY');
        fixture.detectChanges();

        arrow(0, 'ArrowRight');
        expect(buttons()[1].getAttribute('aria-checked')).toBe('true');

        arrow(1, 'ArrowRight');
        expect(buttons()[2].getAttribute('aria-checked')).toBe('true');

        arrow(2, 'ArrowRight');
        expect(buttons()[0].getAttribute('aria-checked')).toBe('true');

        arrow(0, 'ArrowLeft');
        expect(buttons()[2].getAttribute('aria-checked')).toBe('true');
    });

    it('disables every option and ignores clicks when disabled', () => {
        const changed = vi.fn();
        component.changed.subscribe(changed);
        fixture.componentRef.setInput('disabled', true);
        fixture.detectChanges();

        expect(host.getAttribute('aria-disabled')).toBe('true');
        expect(buttons().every((b) => b.disabled)).toBe(true);

        buttons()[1].click();
        fixture.detectChanges();
        expect(buttons().every((b) => b.getAttribute('aria-checked') === 'false')).toBe(true);
        expect(changed).not.toHaveBeenCalled();
    });
});

@Component({
    template: `
        <tum-ui-select-button [options]="opts" optionValue="value" [itemTemplate]="itemTpl" />
        <ng-template #itemTpl let-option
            ><span class="custom-item" [attr.data-testid]="option.value">{{ option.label }}</span></ng-template
        >
    `,
    imports: [TumUiSelectButtonComponent],
})
class TemplateHostComponent {
    readonly opts = [
        { label: 'All', value: 'ALL' },
        { label: 'Runnable', value: 'RUN' },
    ];
}

describe('TumUiSelectButtonComponent (custom item template)', () => {
    it('renders the projected item template with the raw option as context', async () => {
        await TestBed.configureTestingModule({ imports: [TemplateHostComponent] }).compileComponents();
        const fixture = TestBed.createComponent(TemplateHostComponent);
        fixture.detectChanges();
        const custom = Array.from(fixture.nativeElement.querySelectorAll('.custom-item')) as HTMLElement[];
        expect(custom.map((el) => el.textContent?.trim())).toEqual(['All', 'Runnable']);
        expect(custom[0].getAttribute('data-testid')).toBe('ALL');
    });
});

@Component({
    template: `<tum-ui-select-button [options]="opts" optionLabel="label" optionValue="value" [formControl]="control" />`,
    imports: [TumUiSelectButtonComponent, ReactiveFormsModule],
})
class ReactiveHostComponent {
    readonly opts: SpanOption[] = OPTIONS;
    readonly control = new FormControl<string | undefined>('DAY');
}

describe('TumUiSelectButtonComponent (reactive forms)', () => {
    it('binds the FormControl value both ways and honors control.disable()', async () => {
        await TestBed.configureTestingModule({ imports: [ReactiveHostComponent] }).compileComponents();
        const fixture = TestBed.createComponent(ReactiveHostComponent);
        fixture.detectChanges();
        const rendered = Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[];

        expect(rendered[0].getAttribute('aria-checked')).toBe('true');

        rendered[1].click();
        fixture.detectChanges();
        expect(fixture.componentInstance.control.value).toBe('WEEK');

        fixture.componentInstance.control.disable();
        fixture.detectChanges();
        expect(rendered.every((b) => b.disabled)).toBe(true);
    });
});
