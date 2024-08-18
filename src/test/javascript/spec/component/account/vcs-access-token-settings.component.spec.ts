import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AccountService } from 'app/core/auth/account.service';
import { of, throwError } from 'rxjs';
import { By } from '@angular/platform-browser';
import { MockComponent, MockPipe } from 'ng-mocks';
import { ButtonComponent } from 'app/shared/components/button.component';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { User } from 'app/core/user/user.model';
import { ArtemisTestModule } from '../../test.module';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { MockTranslateService, TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { VcsAccessTokensSettingsComponent } from 'app/shared/user-settings/vcs-access-tokens-settings/vcs-access-tokens-settings.component';
import dayjs from 'dayjs/esm';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { AlertService } from 'app/core/util/alert.service';

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
            imports: [ArtemisTestModule],
            declarations: [
                VcsAccessTokensSettingsComponent,
                TranslatePipeMock,
                MockPipe(ArtemisDatePipe),
                MockComponent(ButtonComponent),
                MockComponent(FormDateTimePickerComponent),
            ],
            providers: [
                { provide: AccountService, useValue: accountServiceMock },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: AlertService, useValue: alertServiceMock },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(VcsAccessTokensSettingsComponent);
        comp = fixture.componentInstance;

        translateService = TestBed.inject(TranslateService);
        translateService.currentLang = 'en';

        accountServiceMock.getAuthenticationState.mockReturnValue(of({ id: 1, vcsAccessToken: token, vcsAccessTokenExpiryDate: '11:20' } as User));
        accountServiceMock.addNewVcsAccessToken.mockReturnValue(of({ id: 1, vcsAccessToken: token, vcsAccessTokenExpiryDate: '11:20' } as User));
        accountServiceMock.deleteUserVcsAccessToken.mockReturnValue(of({}));
        jest.spyOn(console, 'error').mockImplementation(() => {});
    });

    it('should cancel token creation', () => {
        accountServiceMock.getAuthenticationState.mockReturnValue(of({ id: 1 } as User));

        startTokenCreation();

        // click button to send expiry date to server, to create the new token
        const createTokenButton = fixture.debugElement.query(By.css('#cancel-vcs-token-creation-button'));
        createTokenButton.triggerEventHandler('onClick', null);
        fixture.detectChanges();
        expect(comp.edit).toBeFalsy();
    });

    it('should fail token creation with invalid date', () => {
        accountServiceMock.getAuthenticationState.mockReturnValue(of({ id: 1 } as User));
        startTokenCreation();

        // add an invalid expiry date
        comp.expiryDate = dayjs().subtract(7, 'day');
        comp.validateDate();
        fixture.detectChanges();

        // click button to send expiry date to server, to create the new token
        const createTokenButton = fixture.debugElement.query(By.css('#create-vcs-token-button'));
        createTokenButton.triggerEventHandler('onClick', null);
        fixture.detectChanges();
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
        fixture.detectChanges();
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
        fixture.detectChanges();

        // click button to send expiry date to server, to create the new token
        const createTokenButton = fixture.debugElement.query(By.css('#create-vcs-token-button'));
        createTokenButton.triggerEventHandler('onClick', null);
        fixture.detectChanges();

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

    it('should set wasCopied to true and back to false after 3 seconds on successful copy', () => {
        comp.ngOnInit();

        jest.useFakeTimers();
        comp.onCopyFinished(true);
        expect(comp.wasCopied).toBeTruthy();
        jest.advanceTimersByTime(3000);
        expect(comp.wasCopied).toBeFalsy();
        jest.useRealTimers();
    });

    it('should not change wasCopied if copy is unsuccessful', () => {
        comp.ngOnInit();
        comp.onCopyFinished(false);

        // Verify that wasCopied remains false
        expect(comp.wasCopied).toBeFalsy();
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
