import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AccountService } from 'app/core/auth/account.service';
import { of, throwError } from 'rxjs';
import { ActivatedRoute, Router } from '@angular/router';
import { ArtemisTestModule } from '../../test.module';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { MockTranslateService, TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { SshUserSettingsKeyDetailsComponent } from 'app/shared/user-settings/ssh-settings/details/ssh-user-settings-key-details.component';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';
import { UserSshPublicKey } from 'app/entities/programming/user-ssh-public-key.model';
import dayjs from 'dayjs/esm';
import { AlertService } from 'app/core/util/alert.service';

describe('SshUserSettingsComponent', () => {
    let fixture: ComponentFixture<SshUserSettingsKeyDetailsComponent>;
    let comp: SshUserSettingsKeyDetailsComponent;
    const mockKey = 'mock-key';
    const invalidKeyFormat = 'invalidKeyFormat';
    const keyAlreadyExists = 'keyAlreadyExists';
    const mockedUserSshKeys = {
        id: 3,
        publicKey: mockKey,
        label: 'Key label',
        keyHash: 'Key hash',
    } as UserSshPublicKey;
    let router: Router;
    let accountServiceMock: {
        getSshPublicKey: jest.Mock;
        addSshPublicKey: jest.Mock;
        addNewSshPublicKey: jest.Mock;
    };
    let alertServiceMock: {
        error: jest.Mock;
        success: jest.Mock;
    };
    let translateService: TranslateService;
    let activatedRoute: MockActivatedRoute;

    beforeEach(async () => {
        accountServiceMock = {
            getSshPublicKey: jest.fn(),
            addSshPublicKey: jest.fn(),
            addNewSshPublicKey: jest.fn(),
        };
        alertServiceMock = {
            error: jest.fn(),
            success: jest.fn(),
        };
        const routerMock = { navigate: jest.fn() };
        await TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [SshUserSettingsKeyDetailsComponent, TranslatePipeMock],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: new MockActivatedRoute({}),
                },
                { provide: AccountService, useValue: accountServiceMock },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: Router, useValue: routerMock },
                { provide: AlertService, useValue: alertServiceMock },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(SshUserSettingsKeyDetailsComponent);
        comp = fixture.componentInstance;
        translateService = TestBed.inject(TranslateService);
        translateService.currentLang = 'en';

        router = TestBed.inject(Router);
        activatedRoute = TestBed.inject(ActivatedRoute) as unknown as MockActivatedRoute;
    });

    it('should initialize view for adding new keys and save new key', () => {
        accountServiceMock.addNewSshPublicKey.mockReturnValue(of({}));
        comp.ngOnInit();
        expect(accountServiceMock.getSshPublicKey).not.toHaveBeenCalled();
        expect(comp.isLoading).toBeFalse();
        comp.displayedSshKey = mockKey;
        comp.displayedExpiryDate = dayjs();
        comp.displayedKeyLabel = 'label';
        comp.validateExpiryDate();
        expect(comp.isExpiryDateValid).toBeTrue();
        comp.saveSshKey();
        expect(alertServiceMock.success).toHaveBeenCalled();
        expect(router.navigate).toHaveBeenCalledWith(['../'], { relativeTo: activatedRoute });
    });

    it('should initialize view for adding new keys and fail saving key', () => {
        const httpError1 = new HttpErrorResponse({
            error: { errorKey: invalidKeyFormat },
            status: 400,
        });
        const httpError2 = new HttpErrorResponse({
            error: { errorKey: keyAlreadyExists },
            status: 400,
        });
        accountServiceMock.addNewSshPublicKey.mockReturnValue(throwError(() => httpError1));
        comp.ngOnInit();
        expect(accountServiceMock.getSshPublicKey).not.toHaveBeenCalled();
        expect(comp.isLoading).toBeFalse();
        comp.displayedSshKey = mockKey;
        comp.displayedExpiryDate = dayjs();
        comp.displayedKeyLabel = 'label';
        comp.validateExpiryDate();
        expect(comp.isExpiryDateValid).toBeTrue();
        comp.saveSshKey();
        accountServiceMock.addNewSshPublicKey.mockReturnValue(throwError(() => httpError2));
        comp.saveSshKey();
        expect(alertServiceMock.error).toHaveBeenCalled();
        expect(router.navigate).not.toHaveBeenCalled();
    });

    it('should initialize key details view with key loaded', async () => {
        accountServiceMock.getSshPublicKey.mockReturnValue(of(mockedUserSshKeys));
        activatedRoute.setParameters({ keyId: 1 });
        comp.ngOnInit();
        expect(accountServiceMock.getSshPublicKey).toHaveBeenCalled();
        expect(comp.isLoading).toBeFalse();
        expect(comp.displayedSshKey).toEqual(mockKey);
        comp.goBack();
        expect(router.navigate).toHaveBeenCalledWith(['../../'], { relativeTo: activatedRoute });
    });

    it('should detect Windows', () => {
        jest.spyOn(window.navigator, 'userAgent', 'get').mockReturnValue('Mozilla/5.0 (Windows NT 10.0; Win64; x64)');
        comp.ngOnInit();
        expect(comp.copyInstructions).toBe('cat ~/.ssh/id_ed25519.pub | clip');
    });

    it('should detect MacOS', () => {
        jest.spyOn(window.navigator, 'userAgent', 'get').mockReturnValue('Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)');
        comp.ngOnInit();
        expect(comp.copyInstructions).toBe('pbcopy < ~/.ssh/id_ed25519.pub');
    });

    it('should detect Linux', () => {
        jest.spyOn(window.navigator, 'userAgent', 'get').mockReturnValue('Mozilla/5.0 (X11; Linux x86_64)');
        comp.ngOnInit();
        expect(comp.copyInstructions).toBe('xclip -selection clipboard < ~/.ssh/id_ed25519.pub');
    });

    it('should detect Android', () => {
        jest.spyOn(window.navigator, 'userAgent', 'get').mockReturnValue('Mozilla/5.0 (Linux; Android 10; Pixel 3)');
        comp.ngOnInit();
        expect(comp.copyInstructions).toBe('termux-clipboard-set < ~/.ssh/id_ed25519.pub');
    });

    it('should detect iOS', () => {
        jest.spyOn(window.navigator, 'userAgent', 'get').mockReturnValue('Mozilla/5.0 (iPhone; CPU iPhone OS 13_5)');
        comp.ngOnInit();
        expect(comp.copyInstructions).toBe('Ctrl + C');
    });

    it('should return Unknown for unrecognized OS', () => {
        jest.spyOn(window.navigator, 'userAgent', 'get').mockReturnValue('Mozilla/5.0 (Unknown OS)');
        comp.ngOnInit();
        expect(comp.copyInstructions).toBe('Ctrl + C');
    });
});
