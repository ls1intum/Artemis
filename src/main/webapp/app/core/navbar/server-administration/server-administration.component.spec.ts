import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { ServerAdministrationComponent } from './server-administration.component';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { By } from '@angular/platform-browser';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { Component } from '@angular/core';
import { MockHasAnyAuthorityDirective } from 'test/helpers/mocks/directive/mock-has-any-authority.directive';

@Component({ template: '' })
class MockEmptyComponent {}

describe('ServerAdministrationComponent', () => {
    let component: ServerAdministrationComponent;
    let fixture: ComponentFixture<ServerAdministrationComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ServerAdministrationComponent, TranslateModule.forRoot()],
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
        expect(component).toBeTruthy();
    });

    it('should emit collapseNavbarListener when collapseNavbar is called', () => {
        const collapseNavbarSpy = jest.spyOn(component.collapseNavbarListener, 'emit');

        component.collapseNavbar();

        expect(collapseNavbarSpy).toHaveBeenCalledWith();
    });

    it('should have default input values as false', () => {
        fixture.detectChanges();

        expect(component.isExamActive()).toBeFalse();
        expect(component.isExamStarted()).toBeFalse();
    });

    it('should handle input properties correctly', () => {
        fixture.componentRef.setInput('isExamActive', true);
        fixture.componentRef.setInput('isExamStarted', true);

        fixture.detectChanges();

        expect(component.isExamActive()).toBeTrue();
        expect(component.isExamStarted()).toBeTrue();
    });

    it('should not show admin link when exam is active', () => {
        fixture.componentRef.setInput('isExamActive', true);
        fixture.detectChanges();

        const adminLink = fixture.debugElement.query(By.css('a[routerLink="/admin"]'));
        expect(adminLink).toBeFalsy();
    });

    it('should not show admin link when exam is started', () => {
        fixture.componentRef.setInput('isExamStarted', true);
        fixture.detectChanges();

        const adminLink = fixture.debugElement.query(By.css('a[routerLink="/admin"]'));
        expect(adminLink).toBeFalsy();
    });

    it('should show admin link when exam is not active and not started', () => {
        fixture.detectChanges();

        const adminLink = fixture.debugElement.query(By.css('a[routerLink="/admin"]'));
        expect(adminLink).toBeTruthy();
    });

    it('should collapse navbar when link is clicked', () => {
        const collapseNavbarSpy = jest.spyOn(component.collapseNavbarListener, 'emit');

        component.onLinkClick();

        expect(collapseNavbarSpy).toHaveBeenCalled();
    });
});
