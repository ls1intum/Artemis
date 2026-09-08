import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TumUiInputDirective } from './tum-ui-input.directive';

@Component({
    imports: [TumUiInputDirective],
    template: `
        <input tumUiInput [invalid]="invalid()" [size]="size()" />
        <textarea tumUiTextarea [tumUiInputInvalid]="legacyInvalid()"></textarea>
    `,
})
class HostComponent {
    readonly invalid = signal(false);
    readonly legacyInvalid = signal(false);
    readonly size = signal<'small' | 'large' | undefined>(undefined);
}

describe('TumUiInputDirective', () => {
    let fixture: ComponentFixture<HostComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
        fixture = TestBed.createComponent(HostComponent);
        fixture.detectChanges();
    });

    function element(selector: string): HTMLElement {
        return fixture.debugElement.query(By.css(selector)).nativeElement as HTMLElement;
    }

    it('emits no aria-invalid while the field is valid', () => {
        expect(element('input').getAttribute('aria-invalid')).toBeNull();
        expect(element('input').getAttribute('data-invalid')).toBeNull();
        expect(element('input').getAttribute('data-slot')).toBe('input');
    });

    it('conveys invalidity to assistive technology, not only in the border colour', () => {
        fixture.componentInstance.invalid.set(true);
        fixture.detectChanges();
        expect(element('input').getAttribute('aria-invalid')).toBe('true');
        expect(element('input').getAttribute('data-invalid')).toBe('true');
        expect(element('input').className).toContain('tum:border-state-danger');
    });

    it('keeps the deprecated prefixed spelling working', () => {
        fixture.componentInstance.legacyInvalid.set(true);
        fixture.detectChanges();
        expect(element('textarea').getAttribute('aria-invalid')).toBe('true');
    });

    it('takes its size from the unprefixed input', () => {
        expect(element('input').className).toContain('tum:text-base');
        fixture.componentInstance.size.set('small');
        fixture.detectChanges();
        expect(element('input').className).toContain('tum:text-sm');
    });
});
