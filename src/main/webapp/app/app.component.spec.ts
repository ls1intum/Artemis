import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { By } from '@angular/platform-browser';
import { AppComponent } from './app.component';
import { MockSyncStorage } from 'test/helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateService } from '@ngx-translate/core';
import { ThemeService } from 'app/core/theme/shared/theme.service';
import { of } from 'rxjs';
import { MockComponent } from 'ng-mocks';
import { AlertOverlayComponent } from 'app/core/alert/alert-overlay.component';
import { RouterModule } from '@angular/router';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { PageRibbonComponent } from 'app/core/layouts/profiles/page-ribbon.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { AccountService } from 'app/core/auth/account.service';
import { SetupPasskeyModalComponent } from 'app/core/course/overview/setup-passkey-modal/setup-passkey-modal.component';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';

class MockThemeService {
    initialize() {
        return of();
    }
}

describe('AppComponent', () => {
    let fixture: ComponentFixture<AppComponent>;
    let comp: AppComponent;
    let modalService: NgbModal;
    let accountService: AccountService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [RouterModule.forRoot([]), SetupPasskeyModalComponent],
            declarations: [AppComponent, MockComponent(AlertOverlayComponent), MockComponent(PageRibbonComponent)],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ThemeService, useClass: MockThemeService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: AccountService, useClass: MockAccountService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(AppComponent);
                comp = fixture.componentInstance;
                modalService = TestBed.inject(NgbModal);
                accountService = TestBed.inject(AccountService);

                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should display footer if there is no exam', () => {
        comp.isExamStarted = false;
        comp.showSkeleton = true;
        fixture.detectChanges();

        const footerElement = fixture.debugElement.query(By.css('jhi-footer'));

        expect(footerElement).not.toBeNull();
    });

    it('should not display footer during an exam', () => {
        comp.isExamStarted = true;
        comp.showSkeleton = true;
        fixture.detectChanges();

        const footerElement = fixture.debugElement.query(By.css('jhi-footer'));

        expect(footerElement).toBeNull();
    });

    describe('openSetupPasskeyModal', () => {
        it('should not open the modal if passkey feature is disabled', () => {
            comp.isPasskeyEnabled = false;
            const openModalSpy = jest.spyOn(modalService, 'open');

            comp.openSetupPasskeyModal();

            expect(openModalSpy).not.toHaveBeenCalled();
        });

        it('should not open the modal if the user is on the login screen', () => {
            comp.isPasskeyEnabled = true;
            const openModalSpy = jest.spyOn(modalService, 'open');
            jest.spyOn(accountService, 'isAuthenticatedSignal').mockReturnValue(false);

            comp.openSetupPasskeyModal();

            expect(openModalSpy).not.toHaveBeenCalled();
        });

        it('should not open the modal if the user has already registered a passkey', () => {
            comp.isPasskeyEnabled = true;
            const openModalSpy = jest.spyOn(modalService, 'open');
            jest.spyOn(accountService, 'isAuthenticatedSignal').mockReturnValue(true);
            accountService.userIdentity = { hasRegisteredAPasskey: true } as any;

            comp.openSetupPasskeyModal();

            expect(openModalSpy).not.toHaveBeenCalled();
        });

        it('should open the modal if the passkey feature is enabled, the user is authenticated, and no passkey is registered', () => {
            comp.isPasskeyEnabled = true;
            const openModalSpy = jest.spyOn(modalService, 'open');
            jest.spyOn(accountService, 'isAuthenticatedSignal').mockReturnValue(true);

            accountService.userIdentity = { hasRegisteredAPasskey: false } as any;

            comp.openSetupPasskeyModal();

            expect(openModalSpy).toHaveBeenCalledWith(SetupPasskeyModalComponent, { size: 'lg', backdrop: 'static' });
        });
    });
});
