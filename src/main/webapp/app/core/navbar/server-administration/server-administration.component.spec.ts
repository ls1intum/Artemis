import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { ServerAdministrationComponent } from './server-administration.component';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { FeatureOverlayComponent } from 'app/shared/components/feature-overlay/feature-overlay.component';
import { MockComponent } from 'ng-mocks';
import { By } from '@angular/platform-browser';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { Component } from '@angular/core';
import { MockHasAnyAuthorityDirective } from 'test/helpers/mocks/directive/mock-has-any-authority.directive';
import { beforeEach, describe, it } from '@jest/globals';

@Component({ template: '' })
class MockEmptyComponent {}

describe('ServerAdministrationComponent', () => {
    let component: ServerAdministrationComponent;
    let fixture: ComponentFixture<ServerAdministrationComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ServerAdministrationComponent, TranslateModule.forRoot(), MockComponent(FeatureOverlayComponent)],
            providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([{ path: '**', component: MockEmptyComponent }])],
        })
            .overrideComponent(ServerAdministrationComponent, {
                remove: { imports: [HasAnyAuthorityDirective] },
                add: { imports: [MockHasAnyAuthorityDirective] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ServerAdministrationComponent);
        component = fixture.componentInstance;
    });

    it('should create', () => {
        expect(component).toBeTrue();
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
        expect(dropdownItem).toBeTruthy();

        const mockClickEvent = {
            button: 0, // Simulates a primary (left) mouse click
            preventDefault: () => {},
        };
        dropdownItem.triggerEventHandler('click', mockClickEvent);
        expect(collapseNavbarSpy).toHaveBeenCalled();
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
