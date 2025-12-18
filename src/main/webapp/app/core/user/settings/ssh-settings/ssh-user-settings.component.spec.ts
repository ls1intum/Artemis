import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { UserSshPublicKey } from 'app/programming/shared/entities/user-ssh-public-key.model';
import { AlertService } from 'app/shared/service/alert.service';
import { SshUserSettingsComponent } from 'app/core/user/settings/ssh-settings/ssh-user-settings.component';
import { SshUserSettingsService } from 'app/core/user/settings/ssh-settings/ssh-user-settings.service';

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
            id: 4,
            publicKey: mockKey,
            label: 'Key label',
            keyHash: 'Key hash 2',
        } as UserSshPublicKey,
    ];
    let alertServiceMock: {
        error: jest.Mock;
    };
    let sshServiceMock: {
        deleteSshPublicKey: jest.Mock;
        getSshPublicKeys: jest.Mock;
    };
    let translateService: TranslateService;

    beforeEach(async () => {
        sshServiceMock = {
            deleteSshPublicKey: jest.fn(),
            getSshPublicKeys: jest.fn(),
        };
        alertServiceMock = {
            error: jest.fn(),
        };
        await TestBed.configureTestingModule({
            providers: [
                { provide: SshUserSettingsService, useValue: sshServiceMock },
                { provide: AlertService, useValue: alertServiceMock },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(SshUserSettingsComponent);
        comp = fixture.componentInstance;
        translateService = TestBed.inject(TranslateService);
        translateService.use('en');
    });

    it('should initialize with User without keys', async () => {
        sshServiceMock.getSshPublicKeys.mockReturnValue(of([] as UserSshPublicKey[]));
        comp.ngOnInit();
        expect(sshServiceMock.getSshPublicKeys).toHaveBeenCalled();
        expect(comp.keyCount).toBe(0);
    });

    it('should initialize with User with keys', async () => {
        sshServiceMock.getSshPublicKeys.mockReturnValue(of(mockedUserSshKeys as UserSshPublicKey[]));
        comp.ngOnInit();
        expect(sshServiceMock.getSshPublicKeys).toHaveBeenCalled();
        expect(comp.sshPublicKeys).toHaveLength(2);
        expect(comp.sshPublicKeys[0].publicKey).toEqual(mockKey);
        expect(comp.keyCount).toBe(2);
    });

    it('should delete SSH key', async () => {
        sshServiceMock.getSshPublicKeys.mockReturnValue(of(mockedUserSshKeys as UserSshPublicKey[]));
        sshServiceMock.deleteSshPublicKey.mockReturnValue(of(new HttpResponse({ status: 200 })));
        comp.ngOnInit();
        comp.deleteSshKey(mockedUserSshKeys[0]);
        expect(sshServiceMock.deleteSshPublicKey).toHaveBeenCalled();
    });

    it('should fail to load SSH keys', () => {
        sshServiceMock.getSshPublicKeys.mockReturnValue(throwError(() => new HttpResponse({ body: new Blob() })));
        comp.ngOnInit();
        expect(comp.keyCount).toBe(0);
        expect(alertServiceMock.error).toHaveBeenCalled();
    });
});
