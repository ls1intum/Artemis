import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AccountService } from 'app/core/auth/account.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { of, throwError } from 'rxjs';
import { User } from 'app/core/user/user.model';
import { ArtemisTestModule } from '../../test.module';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { MockTranslateService, TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { PROFILE_LOCALVC } from 'app/app.constants';
import { SshUserSettingsComponent } from 'app/shared/user-settings/ssh-settings/ssh-user-settings.component';

describe('SshUserSettingsComponent', () => {
    let fixture: ComponentFixture<SshUserSettingsComponent>;
    let comp: SshUserSettingsComponent;
    const mockKey = 'mock-key';

    let accountServiceMock: {
        getAuthenticationState: jest.Mock;
        addSshPublicKey: jest.Mock;
        deleteSshPublicKey: jest.Mock;
    };
    let profileServiceMock: { getProfileInfo: jest.Mock };
    let translateService: TranslateService;

    beforeEach(async () => {
        profileServiceMock = {
            getProfileInfo: jest.fn(),
        };
        accountServiceMock = {
            getAuthenticationState: jest.fn(),
            addSshPublicKey: jest.fn(),
            deleteSshPublicKey: jest.fn(),
        };

        await TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [SshUserSettingsComponent, TranslatePipeMock],
            providers: [
                { provide: AccountService, useValue: accountServiceMock },
                { provide: ProfileService, useValue: profileServiceMock },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: NgbModal, useClass: MockNgbModalService },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(SshUserSettingsComponent);
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
        accountServiceMock.addSshPublicKey.mockReturnValue(of(new HttpResponse({ status: 200 })));
        comp.ngOnInit();
        comp.sshKey = 'new-key';
        comp.editSshKey = true;
        comp.saveSshKey();
        expect(accountServiceMock.addSshPublicKey).toHaveBeenCalledWith('new-key');
        expect(comp.editSshKey).toBeFalse();
    });

    it('should delete SSH key and disable edit mode', () => {
        accountServiceMock.deleteSshPublicKey.mockReturnValue(of(new HttpResponse({ status: 200 })));
        comp.ngOnInit();
        const empty = '';
        comp.sshKey = 'new-key';
        comp.editSshKey = true;
        comp.deleteSshKey();
        expect(accountServiceMock.deleteSshPublicKey).toHaveBeenCalled();
        expect(comp.editSshKey).toBeFalse();
        expect(comp.storedSshKey).toEqual(empty);
    });

    it('should not delete and save on error response', () => {
        const errorResponse = new HttpErrorResponse({ status: 500, statusText: 'Server Error', error: { message: 'Error occurred' } });
        accountServiceMock.deleteSshPublicKey.mockReturnValue(throwError(() => errorResponse));
        accountServiceMock.addSshPublicKey.mockReturnValue(throwError(() => errorResponse));
        comp.ngOnInit();
        const key = 'new-key';
        comp.sshKey = key;
        comp.storedSshKey = key;
        comp.saveSshKey();
        comp.deleteSshKey();
        expect(comp.storedSshKey).toEqual(key);
    });

    it('should cancel editing on cancel', () => {
        accountServiceMock.addSshPublicKey.mockReturnValue(of(new HttpResponse({ status: 200 })));
        comp.ngOnInit();
        const oldKey = 'old-key';
        const newKey = 'new-key';
        comp.sshKey = oldKey;
        comp.editSshKey = true;
        comp.saveSshKey();
        expect(comp.storedSshKey).toEqual(oldKey);
        comp.editSshKey = true;
        comp.sshKey = newKey;
        comp.cancelEditingSshKey();
        expect(comp.storedSshKey).toEqual(oldKey);
    });
});
