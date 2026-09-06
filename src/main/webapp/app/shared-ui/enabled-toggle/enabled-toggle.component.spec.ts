import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ComponentRef } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { EnabledToggleComponent } from 'app/shared-ui/enabled-toggle/enabled-toggle.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('EnabledToggleComponent', () => {
    let comp: EnabledToggleComponent;
    let componentRef: ComponentRef<EnabledToggleComponent>;
    let fixture: ComponentFixture<EnabledToggleComponent>;

    function button(suffix: 'enable' | 'disable'): HTMLButtonElement {
        return fixture.nativeElement.querySelector(`[data-testid="feature-${suffix}"]`);
    }

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [EnabledToggleComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(EnabledToggleComponent);
        comp = fixture.componentInstance;
        componentRef = fixture.componentRef;
        componentRef.setInput('enabled', false);
        componentRef.setInput('testId', 'feature');
        fixture.detectChanges();
    });

    it('should mark the disabled button as active when off', () => {
        expect(button('disable').classList).toContain('enabled-toggle-btn--active-off');
        expect(button('enable').classList).not.toContain('enabled-toggle-btn--active-on');
        expect(button('disable').getAttribute('aria-pressed')).toBe('true');
    });

    it('should mark the enabled button as active when on', () => {
        componentRef.setInput('enabled', true);
        fixture.detectChanges();

        expect(button('enable').classList).toContain('enabled-toggle-btn--active-on');
        expect(button('disable').classList).not.toContain('enabled-toggle-btn--active-off');
        expect(button('enable').getAttribute('aria-pressed')).toBe('true');
    });

    it.each([
        { suffix: 'enable' as const, emitted: true },
        { suffix: 'disable' as const, emitted: false },
    ])('should emit $emitted when the $suffix button is clicked', ({ suffix, emitted }) => {
        const changeSpy = vi.spyOn(comp.enabledChange, 'emit');

        button(suffix).click();

        expect(changeSpy).toHaveBeenCalledExactlyOnceWith(emitted);
    });

    it('should omit the test ids when no test id is given', () => {
        componentRef.setInput('testId', undefined);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelectorAll('[data-testid]')).toHaveLength(0);
    });

    it('should label the group for screen readers', () => {
        componentRef.setInput('ariaLabel', 'Enable feature');
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('[role="group"]').getAttribute('aria-label')).toBe('Enable feature');
    });
});
