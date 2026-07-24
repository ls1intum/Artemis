import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component } from '@angular/core';
import { By } from '@angular/platform-browser';
import { TumUiButtonDirective } from 'app/shared-ui/tum-ui/button/tum-ui-button.directive';

@Component({
    template: `
        <a tumUiButton size="small" severity="info" variant="outlined" data-testid="anchor-btn">Link</a>
        <button tumUiButton data-testid="plain-btn">Go</button>
    `,
    imports: [TumUiButtonDirective],
})
class ButtonDirectiveHostComponent {}

describe('TumUiButtonDirective', () => {
    let fixture: ComponentFixture<ButtonDirectiveHostComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [ButtonDirectiveHostComponent] }).compileComponents();
        fixture = TestBed.createComponent(ButtonDirectiveHostComponent);
        fixture.detectChanges();
    });

    function anchor(): HTMLElement {
        return fixture.debugElement.query(By.css('[data-testid="anchor-btn"]')).nativeElement;
    }
    function plain(): HTMLElement {
        return fixture.debugElement.query(By.css('[data-testid="plain-btn"]')).nativeElement;
    }

    it('applies the shared button base class to a native anchor', () => {
        expect(anchor().className).toContain('tum-ui-btn');
        expect(anchor().tagName).toBe('A');
    });

    it('reflects severity/size/variant via the shared variant class map', () => {
        // outlined info small → transparent bg + info text + small padding
        expect(anchor().className).toContain('text-state-info');
        expect(anchor().className).toContain('bg-transparent');
        expect(anchor().className).toContain('px-2.5');
    });

    it('defaults to primary solid on a native button', () => {
        expect(plain().className).toContain('bg-primary');
        expect(plain().className).toContain('text-surface-0');
        expect(plain().tagName).toBe('BUTTON');
    });

    it('keeps projected content', () => {
        expect(anchor().textContent?.trim()).toBe('Link');
        expect(plain().textContent?.trim()).toBe('Go');
    });
});
