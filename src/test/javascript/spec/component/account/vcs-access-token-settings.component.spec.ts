import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AccountService } from 'app/core/auth/account.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { of } from 'rxjs';
import { User } from 'app/core/user/user.model';
import { ArtemisTestModule } from '../../test.module';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { MockTranslateService, TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { PROFILE_LOCALVC } from 'app/app.constants';
import { VcsAccessTokensSettingsComponent } from 'app/shared/user-settings/vcs-access-tokens-settings/vcs-access-tokens-settings.component';

describe('VcsAccessTokensSettingsComponent', () => {
    let fixture: ComponentFixture<VcsAccessTokensSettingsComponent>;
    let comp: VcsAccessTokensSettingsComponent;

    let accountServiceMock: { getAuthenticationState: jest.Mock; deleteUserVcsAccessToken: jest.Mock; addNewVcsAccessToken: jest.Mock };
    let profileServiceMock: { getProfileInfo: jest.Mock };
    let translateService: TranslateService;

    const token = 'initial-token';

    beforeEach(async () => {
        profileServiceMock = {
            getProfileInfo: jest.fn(),
        };
        accountServiceMock = {
            getAuthenticationState: jest.fn(),
            deleteUserVcsAccessToken: jest.fn(),
            addNewVcsAccessToken: jest.fn(),
        };

        await TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [VcsAccessTokensSettingsComponent, TranslatePipeMock],
            providers: [
                { provide: AccountService, useValue: accountServiceMock },
                { provide: ProfileService, useValue: profileServiceMock },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: NgbModal, useClass: MockNgbModalService },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(VcsAccessTokensSettingsComponent);
        comp = fixture.componentInstance;
        translateService = TestBed.inject(TranslateService);
        translateService.currentLang = 'en';
    });

    beforeEach(() => {
        profileServiceMock.getProfileInfo.mockReturnValue(of({ activeProfiles: [PROFILE_LOCALVC] }));
        accountServiceMock.getAuthenticationState.mockReturnValue(of({ id: 1, vcsAccessToken: token, vcsAccessTokenExpiryDate: '11:20' } as User));
        accountServiceMock.addNewVcsAccessToken.mockReturnValue(of({ id: 1, vcsAccessToken: token, vcsAccessTokenExpiryDate: '11:20' } as User));
        accountServiceMock.deleteUserVcsAccessToken.mockReturnValue(of({}));
    });

    it('should initialize with localVC profile', async () => {
        comp.ngOnInit();
        expect(profileServiceMock.getProfileInfo).toHaveBeenCalled();
        expect(accountServiceMock.getAuthenticationState).toHaveBeenCalled();
        expect(comp.localVCEnabled).toBeTrue();
    });

    it('should initialize with no localVC profile set', async () => {
        profileServiceMock.getProfileInfo.mockReturnValue(of({ activeProfiles: [] }));
        comp.ngOnInit();
        expect(profileServiceMock.getProfileInfo).toHaveBeenCalled();
        expect(accountServiceMock.getAuthenticationState).toHaveBeenCalled();
        expect(comp.localVCEnabled).toBeFalse();
    });

    it('should create new vcs access token', () => {
        const newToken = 'new-token';
        accountServiceMock.addNewVcsAccessToken.mockReturnValue(of({ id: 1, vcsAccessToken: newToken, vcsAccessTokenExpiryDate: '11:20' } as User));
        comp.ngOnInit();
        expect(comp.currentUser.vcsAccessToken).toEqual(token);
        comp.addNewVcsAccessToken();
        expect(accountServiceMock.addNewVcsAccessToken).toHaveBeenCalled();
        expect(comp.currentUser.vcsAccessToken).toEqual(token);
    });

    it('should delete vcs access token', () => {
        const newToken = 'new-token';
        accountServiceMock.addNewVcsAccessToken.mockReturnValue(of({ id: 1, vcsAccessToken: newToken, vcsAccessTokenExpiryDate: '11:20' } as User));
        comp.ngOnInit();
        expect(comp.currentUser.vcsAccessToken).toEqual(token);
        comp.deleteVcsAccessToken();
        expect(accountServiceMock.deleteUserVcsAccessToken).toHaveBeenCalled();
        expect(comp.currentUser.vcsAccessToken).toBeUndefined();
    });
});
