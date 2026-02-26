import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ServerAdministrationComponent } from './server-administration.component';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { By } from '@angular/platform-browser';
import { MockHasAnyAuthorityDirective } from 'test/helpers/mocks/directive/mock-has-any-authority.directive';
import { MockComponent, MockDirective } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { RouterLink, RouterLinkActive } from '@angular/router';

describe('ServerAdministrationComponent', () => {
    let component: ServerAdministrationComponent;
    let fixture: ComponentFixture<ServerAdministrationComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ServerAdministrationComponent],
        })
            .overrideComponent(ServerAdministrationComponent, {
                remove: { imports: [HasAnyAuthorityDirective, TranslateDirective, FaIconComponent, RouterLink, RouterLinkActive] },
                add: {
                    imports: [
                        MockHasAnyAuthorityDirective,
                        MockDirective(TranslateDirective),
                        MockComponent(FaIconComponent),
                        MockDirective(RouterLink),
                        MockDirective(RouterLinkActive),
                    ],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ServerAdministrationComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        fixture.destroy();
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
