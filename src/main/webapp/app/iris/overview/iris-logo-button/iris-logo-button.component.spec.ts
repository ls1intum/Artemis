import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { BehaviorSubject } from 'rxjs';
import { TumAetUiButtonComponent } from '@tumaet/ui-angular';
import { FeatureToggle, FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ButtonSize, ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { IrisLogoButtonComponent } from './iris-logo-button.component';

describe('IrisLogoButtonComponent', () => {
    let fixture: ComponentFixture<IrisLogoButtonComponent>;
    let featuresActive: BehaviorSubject<boolean>;

    beforeEach(async () => {
        featuresActive = new BehaviorSubject(true);
        TestBed.configureTestingModule({
            imports: [IrisLogoButtonComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: FeatureToggleService, useValue: { getFeatureTogglesActive: () => featuresActive } },
            ],
        });
        fixture = TestBed.createComponent(IrisLogoButtonComponent);
        await fixture.whenStable();
    });

    afterEach(() => {
        fixture.destroy();
        vi.restoreAllMocks();
    });

    const button = (): HTMLButtonElement => fixture.nativeElement.querySelector('button');

    it('provides a named rounded icon button when no title is supplied', () => {
        expect(button().getAttribute('aria-label')).toBe('artemisApp.iris.logoButtonLabel');
        const kitButton: TumAetUiButtonComponent = fixture.debugElement.query(By.directive(TumAetUiButtonComponent)).componentInstance;
        expect(kitButton.rounded()).toBe(true);
        expect(button().querySelector('img')?.alt).toBe('');
    });

    it('uses normal presentation when a title is supplied', async () => {
        fixture.componentRef.setInput('title', 'entity.action.generate');
        await fixture.whenStable();
        const kitButton: TumAetUiButtonComponent = fixture.debugElement.query(By.directive(TumAetUiButtonComponent)).componentInstance;
        expect(kitButton.rounded()).toBe(false);
        expect(button().textContent).toContain('entity.action.generate');
        expect(button().hasAttribute('aria-label')).toBe(false);
    });

    it('preserves the requested size and action variant', async () => {
        fixture.componentRef.setInput('btnType', ButtonType.PRIMARY_OUTLINE);
        fixture.componentRef.setInput('btnSize', ButtonSize.SMALL);
        await fixture.whenStable();
        const kitButton: TumAetUiButtonComponent = fixture.debugElement.query(By.directive(TumAetUiButtonComponent)).componentInstance;
        expect(kitButton.severity()).toBe('primary');
        expect(kitButton.variant()).toBe('outlined');
        expect(kitButton.size()).toBe('small');
    });

    it('preserves the native submit type and optional regular button type', async () => {
        expect(button().type).toBe('submit');
        fixture.componentRef.setInput('shouldSubmit', false);
        await fixture.whenStable();
        expect(button().type).toBe('button');
    });

    it('forwards a click once and blocks it while disabled or loading', async () => {
        const clickSpy = vi.spyOn(fixture.componentInstance.onClick, 'emit');
        button().click();
        expect(clickSpy).toHaveBeenCalledOnce();
        fixture.componentRef.setInput('disabled', true);
        await fixture.whenStable();
        button().click();
        expect(button().disabled).toBe(true);
        expect(clickSpy).toHaveBeenCalledOnce();
        fixture.componentRef.setInput('disabled', false);
        fixture.componentRef.setInput('isLoading', true);
        await fixture.whenStable();
        button().click();
        expect(button().disabled).toBe(true);
        expect(button().getAttribute('aria-busy')).toBe('true');
        expect(button().querySelector('img')).toBeNull();
        expect(clickSpy).toHaveBeenCalledOnce();
    });

    it('disables the real button when its feature is disabled and re-enables it', async () => {
        fixture.componentRef.setInput('featureToggle', FeatureToggle.IrisProactiveStruggle);
        featuresActive.next(false);
        await fixture.whenStable();
        expect(button().disabled).toBe(true);
        featuresActive.next(true);
        await fixture.whenStable();
        expect(button().disabled).toBe(false);
    });

    it('shows the translated tooltip on keyboard focus', async () => {
        fixture.componentRef.setInput('tooltip', 'artemisApp.iris.logoButtonLabel');
        await fixture.whenStable();
        button().dispatchEvent(new FocusEvent('focusin', { bubbles: true }));
        await vi.waitFor(() => {
            expect(document.querySelector('[role="tooltip"]')?.textContent).toContain('artemisApp.iris.logoButtonLabel');
        });
    });
});
