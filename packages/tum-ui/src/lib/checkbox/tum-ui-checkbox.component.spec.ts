import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { Component, signal } from '@angular/core';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { TumUiCheckboxChangeEvent, TumUiCheckboxComponent } from './tum-ui-checkbox.component';

describe('TumUiCheckboxComponent', () => {
    let fixture: ComponentFixture<TumUiCheckboxComponent>;
    let component: TumUiCheckboxComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TumUiCheckboxComponent, FontAwesomeTestingModule],
        }).compileComponents();
        fixture = TestBed.createComponent(TumUiCheckboxComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    function input(): HTMLInputElement {
        return fixture.debugElement.query(By.css('input[type="checkbox"]')).nativeElement;
    }

    function tick(): HTMLElement | undefined {
        const icon = fixture.debugElement.query(By.css('.tum-ui-checkbox-icon'));
        return icon ? icon.nativeElement : undefined;
    }

    it('renders a native checkbox and no tick when unchecked', () => {
        expect(input()).not.toBeNull();
        expect(input().checked).toBe(false);
        expect(tick()).toBeUndefined();
    });

    it('reflects the bound checked value and shows the tick when checked', () => {
        fixture.componentRef.setInput('checked', true);
        fixture.detectChanges();
        expect(input().checked).toBe(true);
        expect(tick()).not.toBeUndefined();
    });

    it('emits changed with the new checked value and toggles the model', () => {
        const events: TumUiCheckboxChangeEvent[] = [];
        component.changed.subscribe((event) => events.push(event));

        input().click();
        fixture.detectChanges();

        expect(events).toHaveLength(1);
        expect(events[0].checked).toBe(true);
        expect(events[0].originalEvent).toBeInstanceOf(Event);
        expect(component.checked()).toBe(true);
        expect(tick()).not.toBeUndefined();
    });

    it('forwards inputId, name and aria-label onto the native input', () => {
        fixture.componentRef.setInput('inputId', 'accept');
        fixture.componentRef.setInput('name', 'terms');
        fixture.componentRef.setInput('ariaLabel', 'Accept terms');
        fixture.detectChanges();
        expect(input().id).toBe('accept');
        expect(input().getAttribute('name')).toBe('terms');
        expect(input().getAttribute('aria-label')).toBe('Accept terms');
    });

    it('disables the native input and does not emit on a disabled toggle attempt', () => {
        const onChangeSpy = vi.fn();
        component.changed.subscribe(onChangeSpy);
        fixture.componentRef.setInput('checked', true);
        fixture.componentRef.setInput('disabled', true);
        fixture.detectChanges();

        expect(input().disabled).toBe(true);

        input().click();
        fixture.detectChanges();
        expect(onChangeSpy).not.toHaveBeenCalled();
    });
});

@Component({
    template: `<tum-ui-checkbox [(ngModel)]="value" (changed)="lastEvent = $event" />`,
    imports: [TumUiCheckboxComponent, FormsModule],
})
class TwoWayNgModelHostComponent {
    value = false;
    lastEvent?: TumUiCheckboxChangeEvent;
}

describe('TumUiCheckboxComponent (two-way ngModel via CVA)', () => {
    it('writes into and reads back from a two-way [(ngModel)] binding', async () => {
        await TestBed.configureTestingModule({
            imports: [TwoWayNgModelHostComponent, FontAwesomeTestingModule],
        }).compileComponents();
        const fixture = TestBed.createComponent(TwoWayNgModelHostComponent);
        const host = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();

        const input = fixture.debugElement.query(By.css('input[type="checkbox"]')).nativeElement as HTMLInputElement;
        expect(input.checked).toBe(false);

        input.click();
        fixture.detectChanges();
        await fixture.whenStable();

        expect(host.value).toBe(true);
        expect(host.lastEvent?.checked).toBe(true);
    });
});

@Component({
    template: `<tum-ui-checkbox [ngModel]="selected()" [ngModelOptions]="{ standalone: true }" (changed)="toggle()" />`,
    imports: [TumUiCheckboxComponent, FormsModule],
})
class ControlledOneWayHostComponent {
    readonly selected = signal(false);
    toggle(): void {
        this.selected.update((v) => !v);
    }
}

describe('TumUiCheckboxComponent controlled pattern', () => {
    it('runs the handler and reflects the controlled value flowing back through writeValue', async () => {
        await TestBed.configureTestingModule({
            imports: [ControlledOneWayHostComponent, FontAwesomeTestingModule],
        }).compileComponents();
        const fixture = TestBed.createComponent(ControlledOneWayHostComponent);
        const host = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();

        const input = fixture.debugElement.query(By.css('input[type="checkbox"]')).nativeElement as HTMLInputElement;
        expect(input.checked).toBe(false);

        input.click();
        fixture.detectChanges();
        await fixture.whenStable();

        expect(host.selected()).toBe(true);
        expect(input.checked).toBe(true);
    });
});

@Component({
    template: `<tum-ui-checkbox [formControl]="control" />`,
    imports: [TumUiCheckboxComponent, ReactiveFormsModule],
})
class ReactiveHostComponent {
    readonly control = new FormControl(false);
}

describe('TumUiCheckboxComponent (reactive formControl via CVA)', () => {
    it('syncs with the FormControl value and honors a disabled control', async () => {
        await TestBed.configureTestingModule({
            imports: [ReactiveHostComponent, FontAwesomeTestingModule],
        }).compileComponents();
        const fixture = TestBed.createComponent(ReactiveHostComponent);
        const host = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();

        const input = fixture.debugElement.query(By.css('input[type="checkbox"]')).nativeElement as HTMLInputElement;

        input.click();
        fixture.detectChanges();
        expect(host.control.value).toBe(true);

        host.control.disable();
        fixture.detectChanges();
        expect(input.disabled).toBe(true);
    });
});
