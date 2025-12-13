import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AccountService } from 'app/core/auth/account.service';
import { of, throwError } from 'rxjs';
import { By } from '@angular/platform-browser';
import { User } from 'app/core/user/user.model';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import dayjs from 'dayjs/esm';
import { AlertService } from 'app/shared/service/alert.service';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { provideHttpClient } from '@angular/common/http';
import { VcsAccessTokensSettingsComponent } from 'app/core/user/settings/vcs-access-tokens-settings/vcs-access-tokens-settings.component';

describe('VcsAccessTokensSettingsComponent', () => {
    let fixture: ComponentFixture<VcsAccessTokensSettingsComponent>;
    let comp: VcsAccessTokensSettingsComponent;

    let accountServiceMock: { getAuthenticationState: jest.Mock; deleteUserVcsAccessToken: jest.Mock; addNewVcsAccessToken: jest.Mock };
    const alertServiceMock = { error: jest.fn(), addAlert: jest.fn() };
    let translateService: TranslateService;

    const token = 'initial-token';

    beforeEach(async () => {
        accountServiceMock = {
            getAuthenticationState: jest.fn(),
            deleteUserVcsAccessToken: jest.fn(),
            addNewVcsAccessToken: jest.fn(),
        };

        await TestBed.configureTestingModule({
            imports: [OwlNativeDateTimeModule],
            providers: [
                { provide: AccountService, useValue: accountServiceMock },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: AlertService, useValue: alertServiceMock },
                provideHttpClient(),
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(VcsAccessTokensSettingsComponent);
        comp = fixture.componentInstance;

        translateService = TestBed.inject(TranslateService);
        translateService.currentLang = 'en';

        accountServiceMock.getAuthenticationState.mockReturnValue(of({ id: 1, vcsAccessToken: token, vcsAccessTokenExpiryDate: '11:20' } as User));
        accountServiceMock.addNewVcsAccessToken.mockReturnValue(of({ id: 1, vcsAccessToken: token, vcsAccessTokenExpiryDate: '11:20' } as User));
        accountServiceMock.deleteUserVcsAccessToken.mockReturnValue(of({}));
        // Avoid NG0953: Unexpected emit for destroyed OutputRef for date-time-picker.component.ts
        jest.spyOn(console, 'warn').mockImplementation(() => {});
    });

    afterEach(() => {
        jest.clearAllMocks();
        fixture.destroy();
        TestBed.resetTestingModule();
    });

    it('should cancel token creation', () => {
        accountServiceMock.getAuthenticationState.mockReturnValue(of({ id: 1 } as User));

        startTokenCreation();

        // click button to send expiry date to server, to create the new token
        const createTokenButton = fixture.debugElement.query(By.css('#cancel-vcs-token-creation-button'));
        createTokenButton.triggerEventHandler('onClick', null);
        fixture.changeDetectorRef.detectChanges();
        expect(comp.edit).toBeFalsy();
    });

    it('should fail token creation with invalid date', () => {
        accountServiceMock.getAuthenticationState.mockReturnValue(of({ id: 1 } as User));
        startTokenCreation();

        // add an invalid expiry date
        comp.expiryDate = dayjs().subtract(7, 'day');
        comp.validateDate();
        fixture.changeDetectorRef.detectChanges();

        // click button to send expiry date to server, to create the new token
        const createTokenButton = fixture.debugElement.query(By.css('#create-vcs-token-button'));
        createTokenButton.triggerEventHandler('onClick', null);
        fixture.changeDetectorRef.detectChanges();
        expect(comp.edit).toBeTruthy();
        expect(comp.currentUser?.vcsAccessToken).toBeUndefined();
        expect(alertServiceMock.error).toHaveBeenCalled();
    });

    it('should handle failed token creation', () => {
        accountServiceMock.addNewVcsAccessToken.mockImplementation(() => {
            return throwError(() => new Error('Internal Server error'));
        });

        accountServiceMock.getAuthenticationState.mockReturnValue(of({ id: 1 } as User));
        startTokenCreation();

        // add an invalid expiry date
        comp.expiryDate = dayjs().add(7, 'day');
        comp.validExpiryDate = true;

        // click button to send expiry date to server, to create the new token
        const createTokenButton = fixture.debugElement.query(By.css('#create-vcs-token-button'));
        createTokenButton.triggerEventHandler('onClick', null);
        fixture.changeDetectorRef.detectChanges();
        expect(comp.edit).toBeTruthy();
        expect(alertServiceMock.error).toHaveBeenCalled();
    });

    it('should create new vcs access token', () => {
        const newToken = 'new-token';
        const tokenExpiryDate = dayjs().add(7, 'day');

        accountServiceMock.getAuthenticationState.mockReturnValue(of({ id: 1 } as User));
        accountServiceMock.addNewVcsAccessToken.mockReturnValue(of({ body: { id: 1, vcsAccessToken: newToken, vcsAccessTokenExpiryDate: tokenExpiryDate.toISOString() } as User }));
        startTokenCreation();

        // add an expiry date
        comp.expiryDate = tokenExpiryDate;
        comp.validateDate();
        fixture.changeDetectorRef.detectChanges();

        // click button to send expiry date to server, to create the new token
        const createTokenButton = fixture.debugElement.query(By.css('#create-vcs-token-button'));
        createTokenButton.triggerEventHandler('onClick', null);
        fixture.changeDetectorRef.detectChanges();

        expect(comp.edit).toBeFalsy();
        expect(accountServiceMock.addNewVcsAccessToken).toHaveBeenCalled();
        expect(comp.currentUser!.vcsAccessToken).toEqual(newToken);
    });

    it('should delete vcs access token', () => {
        accountServiceMock.deleteUserVcsAccessToken.mockImplementation(() => {
            return throwError(() => new Error('Internal Server error'));
        });
        comp.ngOnInit();
        comp.deleteVcsAccessToken();
        expect(accountServiceMock.deleteUserVcsAccessToken).toHaveBeenCalled();
        expect(alertServiceMock.error).toHaveBeenCalled();
    });

    it('should handle error when delete vcs access token fails', () => {
        const newToken = 'new-token';
        accountServiceMock.addNewVcsAccessToken.mockReturnValue(of({ id: 1, vcsAccessToken: newToken, vcsAccessTokenExpiryDate: '11:20' } as User));
        comp.ngOnInit();
        expect(comp.currentUser!.vcsAccessToken).toEqual(token);
        comp.deleteVcsAccessToken();
        expect(accountServiceMock.deleteUserVcsAccessToken).toHaveBeenCalled();
        expect(comp.currentUser!.vcsAccessToken).toBeUndefined();
    });

    function startTokenCreation() {
        comp.ngOnInit();
        fixture.detectChanges();
        expect(comp.currentUser!.vcsAccessToken).toBeUndefined();

        // click on new token button
        const addTokenButton = fixture.debugElement.query(By.css('#add-new-token-button'));
        addTokenButton.triggerEventHandler('onClick', null);
        fixture.detectChanges();
        expect(comp.edit).toBeTruthy();
    }
});
