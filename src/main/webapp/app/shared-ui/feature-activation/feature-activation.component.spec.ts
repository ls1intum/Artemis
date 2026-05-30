import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { FeatureActivationComponent } from 'app/shared-ui/feature-activation/feature-activation.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';
import { MockProvider } from 'ng-mocks';
import { faPencil } from '@fortawesome/free-solid-svg-icons';
import { ButtonComponent } from 'app/shared-ui/components/buttons/button/button.component';
import { By } from '@angular/platform-browser';

describe('FeatureActivationComponent', () => {
    setupTestBed({ zoneless: true });
    let component: FeatureActivationComponent;
    let fixture: ComponentFixture<FeatureActivationComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FeatureActivationComponent, ButtonComponent],
            providers: [
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
                MockProvider(FeatureToggleService),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(FeatureActivationComponent);
        fixture.componentRef.setInput('headerTitle', 'Enable Feature Header translation key');
        fixture.componentRef.setInput('description', 'Enable Feature Description translation key');
        fixture.componentRef.setInput('activateButtonText', 'Enable Feature Button translation key');
        fixture.componentRef.setInput('headerIcon', faPencil);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should emit enable event when button is clicked', () => {
        const enableSpy = vi.spyOn(component.enable, 'emit');
        fixture.debugElement.query(By.css('button')).triggerEventHandler('click', null);
        expect(enableSpy).toHaveBeenCalledOnce();
    });

    it('should display the header title, description, and button text based on input', () => {
        const header = fixture.nativeElement.querySelector('h2');
        const description = fixture.nativeElement.querySelector('p');
        const button = fixture.nativeElement.querySelector('jhi-button');

        expect(header.textContent).toContain('Enable Feature Header translation key');
        expect(description.textContent).toContain('Enable Feature Description translation key');
        expect(button.textContent).toContain('Enable Feature Button translation key');
    });

    it('should display the header icon', () => {
        const icon = fixture.nativeElement.querySelector('fa-icon');
        expect(icon).toBeTruthy();
    });

    it('should set loading state', () => {
        fixture.componentRef.setInput('isLoading', true);
        fixture.detectChanges();
        const button = fixture.debugElement.query(By.directive(ButtonComponent));
        expect(button.componentInstance.isLoading()).toBe(true);

        fixture.componentRef.setInput('isLoading', false);
        fixture.detectChanges();
        expect(button.componentInstance.isLoading()).toBe(false);
    });
});
