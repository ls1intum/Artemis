import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
// DeleteDialogService is still built on PrimeNG's dynamic dialog, so its dependency has to be provided here.
import { DialogService } from 'primeng/dynamicdialog';
import { AccountService } from 'app/core/auth/account.service';
import { of, throwError } from 'rxjs';
import { By } from '@angular/platform-browser';
import { User } from 'app/account/user/user.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';
import { AlertService } from 'app/foundation/service/alert.service';
import { provideHttpClient } from '@angular/common/http';
import { TumAetUiDatePickerComponent } from '@tumaet/ui-angular';
import { VcsAccessTokensSettingsComponent } from 'app/account/user/settings/vcs-access-tokens-settings/vcs-access-tokens-settings.component';

describe('VcsAccessTokensSettingsComponent', () => {
    let fixture: ComponentFixture<VcsAccessTokensSettingsComponent>;
    let comp: VcsAccessTokensSettingsComponent;

    let accountServiceMock: {
        getAuthenticationState: ReturnType<typeof vi.fn>;
        deleteUserVcsAccessToken: ReturnType<typeof vi.fn>;
        addNewVcsAccessToken: ReturnType<typeof vi.fn>;
    };
    const alertServiceMock = { error: vi.fn(), addAlert: vi.fn(), success: vi.fn() };
    let translateService: TranslateService;

    const token = 'initial-token';

    beforeEach(async () => {
        accountServiceMock = {
            getAuthenticationState: vi.fn(),
            deleteUserVcsAccessToken: vi.fn(),
            addNewVcsAccessToken: vi.fn(),
        };

        await TestBed.configureTestingModule({
            imports: [],
            providers: [
                { provide: AccountService, useValue: accountServiceMock },
                { provide: TranslateService, useClass: MockTranslateService },
                DialogService,
                { provide: AlertService, useValue: alertServiceMock },
                provideHttpClient(),
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(VcsAccessTokensSettingsComponent);
        comp = fixture.componentInstance;

        translateService = TestBed.inject(TranslateService);
        translateService.use('en');

        accountServiceMock.getAuthenticationState.mockReturnValue(of({ id: 1, vcsAccessToken: token, vcsAccessTokenExpiryDate: '11:20' } as User));
        accountServiceMock.addNewVcsAccessToken.mockReturnValue(of({ id: 1, vcsAccessToken: token, vcsAccessTokenExpiryDate: '11:20' } as User));
        accountServiceMock.deleteUserVcsAccessToken.mockReturnValue(of({}));
        // Avoid NG0953: Unexpected emit for destroyed OutputRef of the date picker
        vi.spyOn(console, 'warn').mockImplementation(() => {});
    });

    afterEach(() => {
        vi.restoreAllMocks();
        fixture.destroy();
        TestBed.resetTestingModule();
    });

    it('should cancel token creation', () => {
        accountServiceMock.getAuthenticationState.mockReturnValue(of({ id: 1 } as User));

        startTokenCreation();

        // click button to send expiry date to server, to create the new token
        const createTokenButton = fixture.debugElement.query(By.css('#cancel-vcs-token-creation-button'));
        createTokenButton.triggerEventHandler('clicked', null);
        fixture.changeDetectorRef.detectChanges();
        expect(comp.edit()).toBeFalsy();
    });

    it('should fail token creation with invalid date', () => {
        accountServiceMock.getAuthenticationState.mockReturnValue(of({ id: 1 } as User));
        startTokenCreation();

        // add an invalid expiry date
        comp.expiryDate.set(dayjs().subtract(7, 'day'));
        comp.validateDate();
        fixture.changeDetectorRef.detectChanges();

        // click button to send expiry date to server, to create the new token
        const createTokenButton = fixture.debugElement.query(By.css('#create-vcs-token-button'));
        createTokenButton.triggerEventHandler('clicked', null);
        fixture.changeDetectorRef.detectChanges();
        expect(comp.edit()).toBeTruthy();
        expect(comp.currentUser()?.vcsAccessToken).toBeUndefined();
        expect(alertServiceMock.error).toHaveBeenCalled();
    });

    it('should limit the calendar to the days from today up to one year ahead', () => {
        accountServiceMock.getAuthenticationState.mockReturnValue(of({ id: 1 } as User));
        startTokenCreation();
        const picker = fixture.debugElement.query(By.directive(TumAetUiDatePickerComponent)).componentInstance as TumAetUiDatePickerComponent;

        expect(picker.min()!.isSame(dayjs(), 'day')).toBe(true);
        expect(picker.max()!.isSame(dayjs().add(1, 'year'), 'day')).toBe(true);
    });

    it('should only enable token creation for a picked date within the next year', () => {
        accountServiceMock.getAuthenticationState.mockReturnValue(of({ id: 1 } as User));
        startTokenCreation();
        const picker = fixture.debugElement.query(By.directive(TumAetUiDatePickerComponent)).componentInstance as TumAetUiDatePickerComponent;
        const createButton = () => fixture.debugElement.query(By.css('#create-vcs-token-button')).componentInstance;
        expect(picker.invalid()).toBe(false);
        expect(createButton().disabled()).toBe(true);

        picker.value.set(dayjs().add(7, 'day'));
        fixture.detectChanges();
        expect(comp.expiryDate()!.isSame(dayjs().add(7, 'day'), 'day')).toBe(true);
        expect(comp.validExpiryDate()).toBe(true);
        expect(picker.invalid()).toBe(false);
        expect(createButton().disabled()).toBe(false);

        picker.value.set(dayjs().subtract(1, 'day'));
        fixture.detectChanges();
        expect(comp.validExpiryDate()).toBe(false);
        expect(picker.invalid()).toBe(true);
        expect(createButton().disabled()).toBe(true);

        picker.value.set(dayjs().add(2, 'year'));
        fixture.detectChanges();
        expect(comp.validExpiryDate()).toBe(false);
        expect(picker.invalid()).toBe(true);
    });

    it('should not allow token creation while the typed text is not a date', () => {
        accountServiceMock.getAuthenticationState.mockReturnValue(of({ id: 1 } as User));
        startTokenCreation();
        comp.onExpiryDateChange(dayjs().add(7, 'day'));
        expect(comp.validExpiryDate()).toBe(true);

        comp.onExpiryTextValidityChange(false);
        expect(comp.validExpiryDate()).toBe(false);

        comp.onExpiryTextValidityChange(true);
        expect(comp.validExpiryDate()).toBe(true);
    });

    it('should reset the entered date when token creation is cancelled', () => {
        accountServiceMock.getAuthenticationState.mockReturnValue(of({ id: 1 } as User));
        startTokenCreation();
        comp.onExpiryDateChange(dayjs().add(7, 'day'));
        comp.onExpiryTextValidityChange(false);

        comp.cancelTokenCreation();
        comp.onExpiryDateChange(dayjs().add(7, 'day'));

        expect(comp.validExpiryDate()).toBe(true);
    });

    it('should handle failed token creation', () => {
        accountServiceMock.addNewVcsAccessToken.mockImplementation(() => {
            return throwError(() => new Error('Internal Server error'));
        });

        accountServiceMock.getAuthenticationState.mockReturnValue(of({ id: 1 } as User));
        startTokenCreation();

        // add an invalid expiry date
        comp.expiryDate.set(dayjs().add(7, 'day'));
        comp.validExpiryDate.set(true);

        // click button to send expiry date to server, to create the new token
        const createTokenButton = fixture.debugElement.query(By.css('#create-vcs-token-button'));
        createTokenButton.triggerEventHandler('clicked', null);
        fixture.changeDetectorRef.detectChanges();
        expect(comp.edit()).toBeTruthy();
        expect(alertServiceMock.error).toHaveBeenCalled();
    });

    it('should create new vcs access token', () => {
        const newToken = 'new-token';
        const tokenExpiryDate = dayjs().add(7, 'day');

        accountServiceMock.getAuthenticationState.mockReturnValue(of({ id: 1 } as User));
        accountServiceMock.addNewVcsAccessToken.mockReturnValue(of({ body: { id: 1, vcsAccessToken: newToken, vcsAccessTokenExpiryDate: tokenExpiryDate.toISOString() } as User }));
        startTokenCreation();

        // add an expiry date
        comp.expiryDate.set(tokenExpiryDate);
        comp.validateDate();
        fixture.changeDetectorRef.detectChanges();

        // click button to send expiry date to server, to create the new token
        const createTokenButton = fixture.debugElement.query(By.css('#create-vcs-token-button'));
        createTokenButton.triggerEventHandler('clicked', null);
        fixture.changeDetectorRef.detectChanges();

        expect(comp.edit()).toBeFalsy();
        expect(accountServiceMock.addNewVcsAccessToken).toHaveBeenCalled();
        expect(comp.currentUser()!.vcsAccessToken).toEqual(newToken);
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
        expect(comp.currentUser()!.vcsAccessToken).toEqual(token);
        comp.deleteVcsAccessToken();
        expect(accountServiceMock.deleteUserVcsAccessToken).toHaveBeenCalled();
        expect(comp.currentUser()!.vcsAccessToken).toBeUndefined();
    });

    function startTokenCreation() {
        comp.ngOnInit();
        fixture.detectChanges();
        expect(comp.currentUser()!.vcsAccessToken).toBeUndefined();

        // click on new token button
        const addTokenButton = fixture.debugElement.query(By.css('#add-new-token-button'));
        addTokenButton.triggerEventHandler('clicked', null);
        fixture.detectChanges();
        expect(comp.edit()).toBeTruthy();
    }
});
