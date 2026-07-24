import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component } from '@angular/core';
import { By } from '@angular/platform-browser';
import { TumUiInputGroupAddonComponent } from 'app/shared-ui/tum-ui/input-group/tum-ui-input-group-addon.component';

@Component({
    template: `<tum-ui-input-group-addon>From</tum-ui-input-group-addon>`,
    imports: [TumUiInputGroupAddonComponent],
})
class AddonHostComponent {}

describe('TumUiInputGroupAddonComponent', () => {
    let fixture: ComponentFixture<AddonHostComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [AddonHostComponent] }).compileComponents();
        fixture = TestBed.createComponent(AddonHostComponent);
        fixture.detectChanges();
    });

    function addon(): HTMLElement {
        return fixture.debugElement.query(By.css('tum-ui-input-group-addon')).nativeElement;
    }

    it('projects its label content', () => {
        expect(addon().textContent?.trim()).toBe('From');
    });

    it('carries the Aura-matched surface tokens (background, border, muted text)', () => {
        const cls = addon().className;
        expect(cls).toContain('tum-ui-input-group-addon');
        expect(cls).toContain('bg-surface-0');
        expect(cls).toContain('text-muted-color');
        expect(cls).toContain('border-y');
        expect(cls).toContain('border-surface-300');
        expect(cls).toContain('dark:bg-surface-950');
        expect(cls).toContain('dark:border-surface-600');
    });

    it('rounds and borders only its outer edges via first:/last: (logical, RTL-safe)', () => {
        const cls = addon().className;
        expect(cls).toContain('first:border-s');
        expect(cls).toContain('first:rounded-s-md');
        expect(cls).toContain('last:border-e');
        expect(cls).toContain('last:rounded-e-md');
    });
});
