import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { Component, signal } from '@angular/core';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { TumUiInputNumberComponent } from './tum-ui-input-number.component';

describe('TumUiInputNumberComponent', () => {
    let fixture: ComponentFixture<TumUiInputNumberComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TumUiInputNumberComponent, FontAwesomeTestingModule],
        }).compileComponents();
        fixture = TestBed.createComponent(TumUiInputNumberComponent);
        fixture.detectChanges();
    });

    afterEach(() => vi.restoreAllMocks());

    function input(): HTMLInputElement {
        return fixture.debugElement.query(By.css('input')).nativeElement;
    }

    it('forwards native input and accessibility attributes', () => {
        fixture.componentRef.setInput('inputId', 'capacity');
        fixture.componentRef.setInput('placeholder', 'Capacity');
        fixture.componentRef.setInput('ariaLabel', 'Capacity');
        fixture.componentRef.setInput('ariaLabelledBy', 'capacity-label');
        fixture.componentRef.setInput('ariaDescribedBy', 'capacity-help');
        fixture.detectChanges();
        expect(input().type).toBe('text');
        expect(input().getAttribute('inputmode')).toBe('numeric');
        expect(input().id).toBe('capacity');
        expect(input().getAttribute('placeholder')).toBe('Capacity');
        expect(input().getAttribute('aria-label')).toBe('Capacity');
        expect(input().getAttribute('aria-labelledby')).toBe('capacity-label');
        expect(input().getAttribute('aria-describedby')).toBe('capacity-help');
    });

    it('does not render the stepper by default and renders two buttons when showButtons is set', () => {
        expect(fixture.debugElement.queryAll(By.css('.tum-ui-input-number-button'))).toHaveLength(0);
        fixture.componentRef.setInput('showButtons', true);
        fixture.detectChanges();
        expect(fixture.debugElement.queryAll(By.css('.tum-ui-input-number-button'))).toHaveLength(2);
    });
});

@Component({
    template: `<tum-ui-input-number
        [(ngModel)]="value"
        [min]="min()"
        [max]="max"
        [showButtons]="true"
        [disabled]="disabled()"
        [prefix]="prefix()"
        [suffix]="suffix()"
        [useGrouping]="grouping()"
        [locale]="locale()"
    />`,
    imports: [TumUiInputNumberComponent, FormsModule, FontAwesomeTestingModule],
})
class HostComponent {
    value?: number;
    readonly min = signal(1);
    max = 5000;
    readonly disabled = signal(false);
    readonly prefix = signal<string | undefined>(undefined);
    readonly suffix = signal<string | undefined>(undefined);
    readonly grouping = signal(true);
    readonly locale = signal<string | undefined>(undefined);
}

describe('TumUiInputNumberComponent (ngModel + formatting)', () => {
    let fixture: ComponentFixture<HostComponent>;
    let host: HostComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
        fixture = TestBed.createComponent(HostComponent);
        host = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();
    });

    function input(): HTMLInputElement {
        return fixture.debugElement.query(By.css('input')).nativeElement;
    }
    function type(text: string): void {
        input().value = text;
        input().dispatchEvent(new Event('input'));
        fixture.detectChanges();
    }
    function buttons(): HTMLButtonElement[] {
        return fixture.debugElement.queryAll(By.css('.tum-ui-input-number-button')).map((d) => d.nativeElement);
    }

    it('formats the written model value with grouping', async () => {
        host.value = 5000;
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
        expect(input().value).toBe('5,000');
    });

    it('parses typed digits into the model (ignoring separators) and clears to undefined when emptied', () => {
        type('1234');
        expect(host.value).toBe(1234);
        type('');
        expect(host.value).toBeUndefined();
    });

    it('renders and preserves the prefix/suffix around the number', async () => {
        host.prefix.set('every ');
        host.suffix.set(' week(s)');
        host.value = 2;
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
        expect(input().value).toBe('every 2 week(s)');
        type('every 3 week(s)');
        expect(host.value).toBe(3);
        expect(input().value).toBe('every 3 week(s)');
    });

    it('clamps to [min,max] on blur', () => {
        type('99999');
        expect(host.value).toBe(99999);
        input().dispatchEvent(new FocusEvent('focus'));
        input().dispatchEvent(new FocusEvent('blur'));
        fixture.detectChanges();
        expect(host.value).toBe(5000);
        expect(input().value).toBe('5,000');
    });

    it('steps up/down by the step and clamps, and responds to ArrowUp/ArrowDown', () => {
        type('3');
        expect(host.value).toBe(3);
        buttons()[0].click();
        fixture.detectChanges();
        expect(host.value).toBe(4);
        buttons()[1].click();
        buttons()[1].click();
        buttons()[1].click();
        buttons()[1].click();
        fixture.detectChanges();
        expect(host.value).toBe(1);

        input().dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowUp' }));
        fixture.detectChanges();
        expect(host.value).toBe(2);
        input().dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
        fixture.detectChanges();
        expect(host.value).toBe(1);
    });

    it('formats with the given locale (German grouping uses a period)', async () => {
        host.locale.set('de');
        host.value = 5000;
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
        expect(input().value).toBe('5.000');
    });

    it('omits grouping when useGrouping is false', async () => {
        host.grouping.set(false);
        host.value = 5000;
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
        expect(input().value).toBe('5000');
    });

    it('exposes WAI-ARIA spinbutton semantics on the input', () => {
        type('5');
        expect(input().getAttribute('role')).toBe('spinbutton');
        expect(input().getAttribute('aria-valuemin')).toBe('1');
        expect(input().getAttribute('aria-valuemax')).toBe('5000');
        expect(input().getAttribute('aria-valuenow')).toBe('5');
    });

    it('steps from an empty field to the lower bound', () => {
        expect(host.value).toBeUndefined();
        buttons()[0].click();
        fixture.detectChanges();
        expect(host.value).toBe(1);
    });

    it('does not step or accept input while disabled', () => {
        host.disabled.set(true);
        fixture.detectChanges();
        expect(input().disabled).toBe(true);
        buttons()[0].click();
        fixture.detectChanges();
        expect(host.value).toBeUndefined();
    });

    it('lets a negative number be typed digit by digit without erasing the leading minus', () => {
        host.min.set(-100);
        fixture.detectChanges();
        type('-');
        expect(input().value).toBe('-');
        expect(host.value).toBeUndefined();
        type('-5');
        expect(host.value).toBe(-5);
    });

    it('preserves the caret position while formatting a mid-string edit', () => {
        type('1234');
        expect(input().value).toBe('1,234');
        input().value = '1,2534';
        input().setSelectionRange(4, 4);
        input().dispatchEvent(new Event('input'));
        fixture.detectChanges();
        expect(host.value).toBe(12534);
        expect(input().value).toBe('12,534');
        expect(input().selectionStart).toBe(4);
    });
});

@Component({
    template: `<tum-ui-input-number [formControl]="control" [min]="1" [max]="5000" [showButtons]="true" />`,
    imports: [TumUiInputNumberComponent, ReactiveFormsModule, FontAwesomeTestingModule],
})
class ReactiveHostComponent {
    readonly control = new FormControl<number | undefined>(undefined);
}

describe('TumUiInputNumberComponent (external CVA writes)', () => {
    let fixture: ComponentFixture<ReactiveHostComponent>;
    let host: ReactiveHostComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [ReactiveHostComponent] }).compileComponents();
        fixture = TestBed.createComponent(ReactiveHostComponent);
        host = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();
    });

    function input(): HTMLInputElement {
        return fixture.debugElement.query(By.css('input')).nativeElement;
    }
    function type(text: string): void {
        input().value = text;
        input().dispatchEvent(new Event('input'));
        fixture.detectChanges();
    }
    async function flush(): Promise<void> {
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    }

    it('reflects setValue in the displayed text while the field is focused', async () => {
        type('1234');
        expect(input().value).toBe('1,234');
        input().dispatchEvent(new FocusEvent('focus'));
        fixture.detectChanges();

        host.control.setValue(42);
        await flush();
        expect(input().value).toBe('42');

        type('427');
        expect(host.control.value).toBe(427);
    });

    it('clears the displayed text when the control is reset while the field is focused', async () => {
        type('1234');
        input().dispatchEvent(new FocusEvent('focus'));
        fixture.detectChanges();

        host.control.reset();
        await flush();
        expect(input().value).toBe('');
        expect(host.control.value).toBeNull();
    });

    it('still reflects setValue while the field is not focused', async () => {
        host.control.setValue(1234);
        await flush();
        expect(input().value).toBe('1,234');
    });
});
