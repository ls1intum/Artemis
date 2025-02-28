import { ComponentFixture, TestBed } from '@angular/core/testing';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faUser } from '@fortawesome/free-solid-svg-icons';
import { IrisLogoSize } from 'app/iris/iris-logo/iris-logo.component';
import { ComponentRef } from '@angular/core';
import { RedirectToIrisButtonComponent } from 'app/shared/metis/redirect-to-iris-button/redirect-to-iris-button.component';
import { faCircleNotch } from '@fortawesome/free-solid-svg-icons';

describe('RedirectToIrisButtonComponent', () => {
    let component: RedirectToIrisButtonComponent;
    let fixture: ComponentFixture<RedirectToIrisButtonComponent>;
    let componentRef: ComponentRef<RedirectToIrisButtonComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({}).compileComponents();

        fixture = TestBed.createComponent(RedirectToIrisButtonComponent);
        component = fixture.componentInstance;
        componentRef = fixture.componentRef;
        fixture.detectChanges();
    });

    it('should have default values', () => {
        expect(component.buttonLoading()).toBe(false);
        expect(component.hideLabelMobile()).toBe(true);
        expect(component.outlined()).toBe(false);
        expect(component.smallButton()).toBe(false);
        expect(component.isButton).toBe(true);
    });

    it('should apply correct @HostBinding classes when inputs change', () => {
        componentRef.setInput('outlined', true);
        componentRef.setInput('smallButton', true);
        fixture.detectChanges();

        const buttonElement = fixture.nativeElement as HTMLButtonElement;
        expect(buttonElement.classList.contains('btn-outline-primary')).toBe(true);
        expect(buttonElement.classList.contains('btn-sm')).toBe(true);
    });

    it('should update the button label when input changes', () => {
        componentRef.setInput('buttonLabel', 'Go to Iris');
        fixture.detectChanges();

        expect(component.buttonLabel()).toBe('Go to Iris');
    });

    it('should display the correct icon when set', () => {
        const testIcon: IconProp = faUser;
        componentRef.setInput('buttonIcon', testIcon);
        fixture.detectChanges();

        expect(component.buttonIcon()).toBe(testIcon);
    });

    it('should set the correct logo size to TEXT when using IrisLogoComponent', () => {
        expect(component.TEXT).toBe(IrisLogoSize.TEXT);
    });

    it('should show loading spinner when buttonLoading is true', () => {
        componentRef.setInput('buttonLoading', true);
        fixture.detectChanges();

        expect(component.buttonLoading()).toBe(true);
        expect(component.faCircleNotch).toBe(faCircleNotch);
    });

    it('should hide label on mobile when hideLabelMobile is true', () => {
        componentRef.setInput('hideLabelMobile', true);
        fixture.detectChanges();

        expect(component.hideLabelMobile()).toBe(true);
    });
});
