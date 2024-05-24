import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AccountInformationComponent } from 'app/shared/user-settings/account-information/account-information.component';
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

describe('AccountInformationComponent', () => {
    let fixture: ComponentFixture<AccountInformationComponent>;
    let comp: AccountInformationComponent;
    const mockKey = 'mock-key';

    let accountServiceMock: { getAuthenticationState: jest.Mock; addSshPublicKey: jest.Mock };
    let profileServiceMock: { getProfileInfo: jest.Mock };
    let translateService: TranslateService;

    beforeEach(async () => {
        profileServiceMock = {
            getProfileInfo: jest.fn(),
        };
        accountServiceMock = {
            getAuthenticationState: jest.fn(),
            addSshPublicKey: jest.fn(),
        };

        await TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [AccountInformationComponent, TranslatePipeMock],
            providers: [
                { provide: AccountService, useValue: accountServiceMock },
                { provide: ProfileService, useValue: profileServiceMock },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: NgbModal, useClass: MockNgbModalService },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(AccountInformationComponent);
        comp = fixture.componentInstance;
        translateService = TestBed.inject(TranslateService);
        translateService.currentLang = 'en';
    });

    beforeEach(() => {
        profileServiceMock.getProfileInfo.mockReturnValue(of({ activeProfiles: [PROFILE_LOCALVC] }));
        accountServiceMock.getAuthenticationState.mockReturnValue(of({ id: 99, sshPublicKey: mockKey } as User));
    });


    it('should initialize with localVC profile', async () => {
        comp.ngOnInit();
        expect(profileServiceMock.getProfileInfo).toHaveBeenCalled();
        expect(accountServiceMock.getAuthenticationState).toHaveBeenCalled();
        expect(comp.localVCEnabled).toBeTrue();
        expect(comp.sshKey).toBe(mockKey);
    });

    it('should initialize with no localVC profile set', async () => {
        profileServiceMock.getProfileInfo.mockReturnValue(of({ activeProfiles: [] }));
        comp.ngOnInit();
        expect(profileServiceMock.getProfileInfo).toHaveBeenCalled();
        expect(accountServiceMock.getAuthenticationState).toHaveBeenCalled();
        expect(comp.localVCEnabled).toBeFalse();
    });

    it('should save SSH key and disable edit mode', () => {
        accountServiceMock.addSshPublicKey.mockReturnValue(of());
        comp.ngOnInit();
        comp.sshKey = 'new-key';
        comp.editSshKey = true;
        comp.saveSshKey();
        expect(accountServiceMock.addSshPublicKey).toHaveBeenCalledWith('new-key');
        expect(comp.editSshKey).toBeFalse();
    });
});
