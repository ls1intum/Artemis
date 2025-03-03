import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { of } from 'rxjs';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { PROFILE_LOCALVC } from 'app/app.constants';
import { UserSettingsContainerComponent } from 'app/shared/user-settings/user-settings-container/user-settings-container.component';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';

describe('UserSettingsContainerComponent', () => {
    let fixture: ComponentFixture<UserSettingsContainerComponent>;
    let comp: UserSettingsContainerComponent;

    let profileServiceMock: { getProfileInfo: jest.Mock };
    let translateService: TranslateService;

    const router = new MockRouter();
    router.setUrl('');

    beforeEach(async () => {
        profileServiceMock = {
            getProfileInfo: jest.fn(),
        };

        await TestBed.configureTestingModule({
            imports: [UserSettingsContainerComponent, RouterModule],
            providers: [
                { provide: ProfileService, useValue: profileServiceMock },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: Router, useValue: router },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: AccountService, useClass: MockAccountService },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(UserSettingsContainerComponent);
        comp = fixture.componentInstance;
        translateService = TestBed.inject(TranslateService);
        translateService.currentLang = 'en';
    });

    beforeEach(() => {
        profileServiceMock.getProfileInfo.mockReturnValue(of({ activeProfiles: [PROFILE_LOCALVC] }));
    });

    it('should initialize with localVC profile', async () => {
        comp.ngOnInit();
        expect(profileServiceMock.getProfileInfo).toHaveBeenCalled();
        expect(comp.localVCEnabled).toBeTrue();
    });

    it('should initialize with no localVC profile set', async () => {
        profileServiceMock.getProfileInfo.mockReturnValue(of({ activeProfiles: [] }));
        comp.ngOnInit();
        expect(profileServiceMock.getProfileInfo).toHaveBeenCalled();
        expect(comp.localVCEnabled).toBeFalse();
    });
});
