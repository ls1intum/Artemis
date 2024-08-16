import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AccountService } from 'app/core/auth/account.service';
import { of } from 'rxjs';
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

describe('VcsAccessTokensSettingsComponent', () => {
    let fixture: ComponentFixture<VcsAccessTokensSettingsComponent>;
    let comp: VcsAccessTokensSettingsComponent;

    let accountServiceMock: { getAuthenticationState: jest.Mock; deleteUserVcsAccessToken: jest.Mock; addNewVcsAccessToken: jest.Mock };
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

        comp.ngOnInit();
        fixture.detectChanges();
        expect(comp.currentUser!.vcsAccessToken).toBeUndefined();

        // start token creation
        const addTokenButton = fixture.debugElement.query(By.css('#add-new-token-button'));
        addTokenButton.triggerEventHandler('onClick', null);
        fixture.detectChanges();
        expect(comp.edit).toBeTruthy();

        // click button to send expiry date to server, to create the new token
        const createTokenButton = fixture.debugElement.query(By.css('#cancel-vcs-token-creation-button'));
        createTokenButton.triggerEventHandler('onClick', null);
        fixture.detectChanges();
        expect(comp.edit).toBeFalsy();
    });

    it('should create new vcs access token', () => {
        const newToken = 'new-token';
        const tokenExpiryDate = dayjs().add(7, 'day');

        accountServiceMock.getAuthenticationState.mockReturnValue(of({ id: 1 } as User));
        accountServiceMock.addNewVcsAccessToken.mockReturnValue(of({ body: { id: 1, vcsAccessToken: newToken, vcsAccessTokenExpiryDate: tokenExpiryDate.toISOString() } as User }));
        comp.ngOnInit();
        fixture.detectChanges();
        expect(comp.currentUser!.vcsAccessToken).toBeUndefined();

        // start token creation
        const addTokenButton = fixture.debugElement.query(By.css('#add-new-token-button'));
        addTokenButton.triggerEventHandler('onClick', null);
        expect(comp.edit).toBeTruthy();

        // add a expiry date
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
        const newToken = 'new-token';
        accountServiceMock.addNewVcsAccessToken.mockReturnValue(of({ id: 1, vcsAccessToken: newToken, vcsAccessTokenExpiryDate: '11:20' } as User));
        comp.ngOnInit();
        expect(comp.currentUser!.vcsAccessToken).toEqual(token);
        comp.deleteVcsAccessToken();
        expect(accountServiceMock.deleteUserVcsAccessToken).toHaveBeenCalled();
        expect(comp.currentUser!.vcsAccessToken).toBeUndefined();
    });
});
