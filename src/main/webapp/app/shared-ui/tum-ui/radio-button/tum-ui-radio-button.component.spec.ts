import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { Component, signal } from '@angular/core';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { TumUiRadioButtonClickEvent, TumUiRadioButtonComponent } from 'app/shared-ui/tum-ui/radio-button/tum-ui-radio-button.component';

describe('TumUiRadioButtonComponent', () => {
    let fixture: ComponentFixture<TumUiRadioButtonComponent>;
    let component: TumUiRadioButtonComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TumUiRadioButtonComponent],
        }).compileComponents();
        fixture = TestBed.createComponent(TumUiRadioButtonComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('value', 'a');
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    function input(): HTMLInputElement {
        return fixture.debugElement.query(By.css('input[type="radio"]')).nativeElement;
    }

    function box(): HTMLElement {
        return fixture.debugElement.query(By.css('.tum-ui-radio-button-box')).nativeElement;
    }

    it('renders a native radio, unchecked when the selected value differs from its value', () => {
        expect(input()).not.toBeNull();
        expect(input().type).toBe('radio');
        expect(input().checked).toBe(false);
        expect(box().className).toContain('bg-surface-0');
    });

    it('renders checked (primary fill) when the bound modelValue equals its value', () => {
        fixture.componentRef.setInput('modelValue', 'a');
        fixture.detectChanges();
        expect(input().checked).toBe(true);
        expect(box().className).toContain('bg-primary');
    });

    it('treats an undefined selected value as unchecked (the admin value-or-undefined pattern)', () => {
        fixture.componentRef.setInput('modelValue', undefined);
        fixture.detectChanges();
        expect(input().checked).toBe(false);
    });

    it('emits onClick with the radio value on every click', () => {
        const events: TumUiRadioButtonClickEvent[] = [];
        component.onClick.subscribe((event) => events.push(event));

        input().click();
        input().click();
        fixture.detectChanges();

        expect(events).toHaveLength(2);
        expect(events[0].value).toBe('a');
        expect(events[0].originalEvent).toBeInstanceOf(MouseEvent);
    });

    it('forwards inputId, name and aria-label onto the native input', () => {
        fixture.componentRef.setInput('inputId', 'opt-a');
        fixture.componentRef.setInput('name', 'group');
        fixture.componentRef.setInput('ariaLabel', 'Option A');
        fixture.detectChanges();
        expect(input().id).toBe('opt-a');
        expect(input().getAttribute('name')).toBe('group');
        expect(input().getAttribute('aria-label')).toBe('Option A');
    });

    it('does not emit onClick when disabled', () => {
        const onClickSpy = vi.fn();
        component.onClick.subscribe(onClickSpy);
        fixture.componentRef.setInput('disabled', true);
        fixture.detectChanges();

        expect(input().disabled).toBe(true);
        // Guarded in the handler (a disabled native input also does not dispatch click on user interaction).
        component['onInputClick'](new MouseEvent('click'));
        expect(onClickSpy).not.toHaveBeenCalled();
    });
});

@Component({
    // Mirrors the admin user-management pattern: a group sharing `name`, each radio one-way
    // [ngModel]="selected === value ? value : undefined" plus (onClick) that sets the source of truth.
    template: `
        @for (option of options; track option) {
            <tum-ui-radio-button
                [value]="option"
                name="group"
                [ngModel]="selected() === option ? option : undefined"
                [ngModelOptions]="{ standalone: true }"
                (onClick)="select(option)"
            />
        }
    `,
    imports: [TumUiRadioButtonComponent, FormsModule],
})
class OneWayGroupHostComponent {
    readonly options = ['a', 'b', 'c'];
    readonly selected = signal<string | undefined>(undefined);
    select(option: string): void {
        this.selected.set(option);
    }
}

describe('TumUiRadioButtonComponent (one-way [ngModel] + (onClick) group)', () => {
    it('checks exactly the radio whose value matches the selection and updates on click', async () => {
        await TestBed.configureTestingModule({
            imports: [OneWayGroupHostComponent],
        }).compileComponents();
        const fixture = TestBed.createComponent(OneWayGroupHostComponent);
        const host = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();

        const inputs = fixture.debugElement.queryAll(By.css('input[type="radio"]')).map((d) => d.nativeElement as HTMLInputElement);
        expect(inputs.map((i) => i.checked)).toEqual([false, false, false]);

        inputs[1].click();
        fixture.detectChanges();
        await fixture.whenStable();

        expect(host.selected()).toBe('b');
        expect(inputs.map((i) => i.checked)).toEqual([false, true, false]);
    });
});

@Component({
    template: `
        <tum-ui-radio-button [value]="false" name="exec" [(ngModel)]="executeNow" />
        <tum-ui-radio-button [value]="true" name="exec" [(ngModel)]="executeNow" />
    `,
    imports: [TumUiRadioButtonComponent, FormsModule],
})
class TwoWayGroupHostComponent {
    executeNow = false;
}

describe('TumUiRadioButtonComponent (two-way [(ngModel)] group via CVA)', () => {
    it('reflects the initial model and writes the picked value back (data-export dialog pattern)', async () => {
        await TestBed.configureTestingModule({
            imports: [TwoWayGroupHostComponent],
        }).compileComponents();
        const fixture = TestBed.createComponent(TwoWayGroupHostComponent);
        const host = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();
        // NgModel's initial writeValue resolves on a microtask; flush it to the [checked] bindings.
        fixture.detectChanges();

        const inputs = fixture.debugElement.queryAll(By.css('input[type="radio"]')).map((d) => d.nativeElement as HTMLInputElement);
        const boxes = fixture.debugElement.queryAll(By.css('.tum-ui-radio-button-box')).map((d) => d.nativeElement as HTMLElement);
        // executeNow === false -> the [value]="false" radio is checked and visually filled.
        expect(inputs[0].checked).toBe(true);
        expect(inputs[1].checked).toBe(false);
        expect(boxes[0].className).toContain('bg-primary');
        expect(boxes[1].className).not.toContain('bg-primary');

        inputs[1].click();
        fixture.detectChanges();
        await fixture.whenStable();
        // The sibling's writeValue (from the shared model changing) resolves on a microtask, like the initial one.
        fixture.detectChanges();

        expect(host.executeNow).toBe(true);
        expect(inputs[0].checked).toBe(false);
        expect(inputs[1].checked).toBe(true);
        // Regression: the CLICKED radio must also become visually filled. NgModel never calls writeValue back on
        // the initiating accessor, so the radio must reflect its own selection — otherwise (as reported on the
        // data-export dialog) switching left neither radio filled.
        expect(boxes[1].className).toContain('bg-primary');
        expect(boxes[0].className).not.toContain('bg-primary');
    });
});

@Component({
    template: `<tum-ui-radio-button [value]="'x'" [formControl]="control" />`,
    imports: [TumUiRadioButtonComponent, ReactiveFormsModule],
})
class ReactiveHostComponent {
    readonly control = new FormControl<string | undefined>(undefined);
}

describe('TumUiRadioButtonComponent (reactive formControl via CVA)', () => {
    it('honors a disabled control', async () => {
        await TestBed.configureTestingModule({
            imports: [ReactiveHostComponent],
        }).compileComponents();
        const fixture = TestBed.createComponent(ReactiveHostComponent);
        const host = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();

        const input = fixture.debugElement.query(By.css('input[type="radio"]')).nativeElement as HTMLInputElement;
        expect(input.checked).toBe(false);

        input.click();
        fixture.detectChanges();
        expect(host.control.value).toBe('x');

        host.control.disable();
        fixture.detectChanges();
        expect(input.disabled).toBe(true);
    });
});
