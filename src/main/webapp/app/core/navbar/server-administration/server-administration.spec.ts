import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { TranslateModule } from '@ngx-translate/core';
import { ServerAdministration } from './server-administration';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { FeatureOverlayComponent } from 'app/shared/components/feature-overlay/feature-overlay.component';
import { MockComponent, MockDirective } from 'ng-mocks';
import { By } from '@angular/platform-browser';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

describe('ServerAdministration', () => {
    let component: ServerAdministration;
    let fixture: ComponentFixture<ServerAdministration>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ServerAdministration, RouterTestingModule, TranslateModule.forRoot(), MockDirective(HasAnyAuthorityDirective), MockComponent(FeatureOverlayComponent)],
            providers: [provideHttpClient(), provideHttpClientTesting()],
        }).compileComponents();

        fixture = TestBed.createComponent(ServerAdministration);
        component = fixture.componentInstance;
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should emit collapseNavbarListener when collapseNavbar is called', () => {
        const collapseNavbarSpy = jest.spyOn(component.collapseNavbarListener, 'emit');

        component.collapseNavbar();

        expect(collapseNavbarSpy).toHaveBeenCalledWith();
    });

    it('should emit collapseNavbarListener when dropdown item is clicked', () => {
        const collapseNavbarSpy = jest.spyOn(component.collapseNavbarListener, 'emit');
        fixture.detectChanges();

        const dropdownItem = fixture.debugElement.query(By.css('a[routerLink]'));
        if (dropdownItem) {
            dropdownItem.triggerEventHandler('click', null);
            expect(collapseNavbarSpy).toHaveBeenCalled();
        }
    });

    it('should handle input properties correctly', () => {
        fixture.componentRef.setInput('isExamActive', true);
        fixture.componentRef.setInput('isExamStarted', true);
        fixture.componentRef.setInput('localCIActive', true);
        fixture.componentRef.setInput('irisEnabled', true);
        fixture.componentRef.setInput('ltiEnabled', true);
        fixture.componentRef.setInput('standardizedCompetenciesEnabled', true);
        fixture.componentRef.setInput('atlasEnabled', true);
        fixture.componentRef.setInput('examEnabled', true);

        fixture.detectChanges();

        expect(component.isExamActive()).toBeTrue();
        expect(component.isExamStarted()).toBeTrue();
        expect(component.localCIActive()).toBeTrue();
        expect(component.irisEnabled()).toBeTrue();
        expect(component.ltiEnabled()).toBeTrue();
        expect(component.standardizedCompetenciesEnabled()).toBeTrue();
        expect(component.atlasEnabled()).toBeTrue();
        expect(component.examEnabled()).toBeTrue();
    });

    it('should have default input values as false', () => {
        fixture.detectChanges();

        expect(component.isExamActive()).toBeFalse();
        expect(component.isExamStarted()).toBeFalse();
        expect(component.localCIActive()).toBeFalse();
        expect(component.irisEnabled()).toBeFalse();
        expect(component.ltiEnabled()).toBeFalse();
        expect(component.standardizedCompetenciesEnabled()).toBeFalse();
        expect(component.atlasEnabled()).toBeFalse();
        expect(component.examEnabled()).toBeFalse();
    });
});
