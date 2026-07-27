import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, signal } from '@angular/core';
import { By } from '@angular/platform-browser';
import { TumUiInputDirective } from 'app/shared-ui/tum-ui/input/tum-ui-input.directive';

@Component({
    template: `
        <input tumUiInput class="w-full" [tumUiInputSize]="size()" [tumUiInputInvalid]="invalid()" />
        <textarea tumUiInput></textarea>
        <textarea tumUiTextarea></textarea>
    `,
    imports: [TumUiInputDirective],
})
class InputHostComponent {
    size = signal<'small' | 'large' | undefined>(undefined);
    invalid = signal(false);
}

describe('TumUiInputDirective', () => {
    let fixture: ComponentFixture<InputHostComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [InputHostComponent] }).compileComponents();
        fixture = TestBed.createComponent(InputHostComponent);
        fixture.detectChanges();
    });

    function input(): HTMLInputElement {
        return fixture.debugElement.query(By.css('input[tumUiInput]')).nativeElement;
    }

    it('applies the base p-inputtext-matched classes to a native input', () => {
        const cls = input().className;
        expect(cls).toContain('tum-ui-input');
        expect(cls).toContain('rounded-md');
        expect(cls).toContain('border');
        expect(cls).toContain('bg-surface-0');
        expect(cls).toContain('text-surface-700');
        expect(cls).toContain('shadow-xs');
        expect(cls).toContain('placeholder:text-surface-500');
        expect(cls).toContain('dark:bg-surface-950');
    });

    it('merges the directive classes with static template classes (does not clobber class="w-full")', () => {
        const cls = input().className;
        expect(cls).toContain('w-full');
        expect(cls).toContain('tum-ui-input');
    });

    it('defaults to the normal size and the valid border', () => {
        const cls = input().className;
        expect(cls).toContain('px-3');
        expect(cls).toContain('py-2');
        expect(cls).toContain('text-base');
        expect(cls).toContain('border-surface-300');
        expect(cls).toContain('enabled:focus:border-primary');
        expect(cls).not.toContain('border-state-danger');
    });

    it('applies the small size variant', () => {
        fixture.componentInstance.size.set('small');
        fixture.detectChanges();
        const cls = input().className;
        expect(cls).toContain('text-sm');
        expect(cls).toContain('px-2.5');
        expect(cls).toContain('py-1.5');
    });

    it('applies the large size variant', () => {
        fixture.componentInstance.size.set('large');
        fixture.detectChanges();
        const cls = input().className;
        expect(cls).toContain('text-lg');
        expect(cls).toContain('px-3.5');
        expect(cls).toContain('py-2.5');
    });

    it('swaps to a fixed danger border when invalid (and drops the hover/focus border)', () => {
        fixture.componentInstance.invalid.set(true);
        fixture.detectChanges();
        const cls = input().className;
        expect(cls).toContain('border-state-danger');
        expect(cls).not.toContain('border-surface-300');
        expect(cls).not.toContain('enabled:focus:border-primary');
    });

    it('styles a textarea via both the tumUiInput and tumUiTextarea selectors', () => {
        const textareas = fixture.debugElement.queryAll(By.css('textarea'));
        expect(textareas).toHaveLength(2);
        for (const textarea of textareas) {
            const cls = (textarea.nativeElement as HTMLTextAreaElement).className;
            expect(cls).toContain('tum-ui-input');
            expect(cls).toContain('rounded-md');
            expect(cls).toContain('bg-surface-0');
        }
    });
});
