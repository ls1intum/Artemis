import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { TumUiInputNumberComponent } from 'app/shared-ui/tum-ui/input-number/tum-ui-input-number.component';

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

    it('renders a text input with a numeric inputmode and forwards inputId/placeholder/aria-label', () => {
        fixture.componentRef.setInput('inputId', 'capacity');
        fixture.componentRef.setInput('placeholder', 'Capacity');
        fixture.componentRef.setInput('ariaLabel', 'Capacity');
        fixture.detectChanges();
        expect(input().type).toBe('text');
        expect(input().getAttribute('inputmode')).toBe('numeric');
        expect(input().id).toBe('capacity');
        expect(input().getAttribute('placeholder')).toBe('Capacity');
        expect(input().getAttribute('aria-label')).toBe('Capacity');
    });

    it('does not render the stepper by default and renders two buttons when showButtons is set', () => {
        expect(fixture.debugElement.queryAll(By.css('.tum-ui-input-number-button'))).toHaveLength(0);
        fixture.componentRef.setInput('showButtons', true);
        fixture.detectChanges();
        expect(fixture.debugElement.queryAll(By.css('.tum-ui-input-number-button'))).toHaveLength(2);
    });

    it('adds the fluid host class when fluid is set', () => {
        fixture.componentRef.setInput('fluid', true);
        fixture.detectChanges();
        expect((fixture.nativeElement as HTMLElement).className).toContain('tum-ui-input-number-fluid');
    });
});

@Component({
    template: `<tum-ui-input-number [(ngModel)]="value" [min]="min" [max]="max" [showButtons]="true" [prefix]="prefix()" [suffix]="suffix()" [useGrouping]="grouping()" />`,
    imports: [TumUiInputNumberComponent, FormsModule, FontAwesomeTestingModule],
})
class HostComponent {
    value?: number;
    min = 1;
    max = 5000;
    readonly prefix = signal<string | undefined>(undefined);
    readonly suffix = signal<string | undefined>(undefined);
    readonly grouping = signal(true);
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
        // typing a new number keeps the affixes
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
        type('3'); // seed synchronously through the input event
        expect(host.value).toBe(3);
        buttons()[0].click(); // increment
        fixture.detectChanges();
        expect(host.value).toBe(4);
        buttons()[1].click(); // decrement
        buttons()[1].click();
        buttons()[1].click();
        buttons()[1].click();
        fixture.detectChanges();
        expect(host.value).toBe(1); // clamped at min

        input().dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowUp' }));
        fixture.detectChanges();
        expect(host.value).toBe(2);
        input().dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
        fixture.detectChanges();
        expect(host.value).toBe(1);
    });

    it('omits grouping when useGrouping is false', async () => {
        host.grouping.set(false);
        host.value = 5000;
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
        expect(input().value).toBe('5000');
    });
});
