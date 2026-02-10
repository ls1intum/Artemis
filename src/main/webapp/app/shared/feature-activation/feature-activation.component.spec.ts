import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FeatureActivationComponent } from 'app/shared/feature-activation/feature-activation.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { MockProvider } from 'ng-mocks';
import { faPencil } from '@fortawesome/free-solid-svg-icons';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { By } from '@angular/platform-browser';

describe('FeatureActivationComponent', () => {
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

    it('should emit enable event when button is clicked', () => {
        const enableSpy = jest.spyOn(component.enable, 'emit');
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
        expect(button.componentInstance.isLoading()).toBeTrue();

        fixture.componentRef.setInput('isLoading', false);
        fixture.detectChanges();
        expect(button.componentInstance.isLoading()).toBeFalse();
    });
});
