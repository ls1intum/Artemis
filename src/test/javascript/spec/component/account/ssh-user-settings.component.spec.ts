import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AccountService } from 'app/core/auth/account.service';
import { of, throwError } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { MockTranslateService, TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { SshUserSettingsComponent } from 'app/shared/user-settings/ssh-settings/ssh-user-settings.component';
import { UserSshPublicKey } from 'app/entities/programming/user-ssh-public-key.model';
import { AlertService } from 'app/core/util/alert.service';

describe('SshUserSettingsComponent', () => {
    let fixture: ComponentFixture<SshUserSettingsComponent>;
    let comp: SshUserSettingsComponent;
    const mockKey = 'ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIJxKWdvcbNTWl4vBjsijoY5HN5dpjxU40huy1PFpdd2o comment';
    const mockedUserSshKeys = [
        {
            id: 3,
            publicKey: mockKey,
            label: 'Key label',
            keyHash: 'Key hash',
        } as UserSshPublicKey,
        {
            id: 3,
            publicKey: mockKey,
            label: 'Key label',
            keyHash: 'Key hash 2',
        } as UserSshPublicKey,
    ];
    let alertServiceMock: {
        error: jest.Mock;
    };
    let accountServiceMock: {
        getAuthenticationState: jest.Mock;
        deleteSshPublicKey: jest.Mock;
        getAllSshPublicKeys: jest.Mock;
    };
    let translateService: TranslateService;

    beforeEach(async () => {
        accountServiceMock = {
            getAuthenticationState: jest.fn(),
            deleteSshPublicKey: jest.fn(),
            getAllSshPublicKeys: jest.fn(),
        };
        alertServiceMock = {
            error: jest.fn(),
        };
        await TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [SshUserSettingsComponent, TranslatePipeMock],
            providers: [
                { provide: AccountService, useValue: accountServiceMock },
                { provide: AlertService, useValue: alertServiceMock },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(SshUserSettingsComponent);
        comp = fixture.componentInstance;
        translateService = TestBed.inject(TranslateService);
        translateService.currentLang = 'en';
    });

    it('should initialize with User without keys', async () => {
        accountServiceMock.getAllSshPublicKeys.mockReturnValue(of([] as UserSshPublicKey[]));
        comp.ngOnInit();
        expect(accountServiceMock.getAllSshPublicKeys).toHaveBeenCalled();
        expect(comp.keyCount).toBe(0);
    });

    it('should initialize with User with keys', async () => {
        accountServiceMock.getAllSshPublicKeys.mockReturnValue(of(mockedUserSshKeys as UserSshPublicKey[]));
        comp.ngOnInit();
        expect(accountServiceMock.getAllSshPublicKeys).toHaveBeenCalled();
        expect(comp.keyCount).toBe(2);
    });

    it('should delete SSH key', async () => {
        accountServiceMock.getAllSshPublicKeys.mockReturnValue(of(mockedUserSshKeys as UserSshPublicKey[]));
        accountServiceMock.deleteSshPublicKey.mockReturnValue(of(new HttpResponse({ status: 200 })));
        comp.ngOnInit();
        comp.deleteSshKey(mockedUserSshKeys[0]);
        expect(accountServiceMock.deleteSshPublicKey).toHaveBeenCalled();
    });

    it('should fail to load SSH keys', () => {
        accountServiceMock.getAllSshPublicKeys.mockReturnValue(throwError(() => new HttpResponse({ body: new Blob() })));
        comp.ngOnInit();
        expect(comp.keyCount).toBe(0);
        expect(alertServiceMock.error).toHaveBeenCalled();
    });
});
