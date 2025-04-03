import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';
import { UserSettingsContainerComponent } from 'app/core/user/settings/user-settings-container/user-settings-container.component';

describe('UserSettingsContainerComponent', () => {
    let fixture: ComponentFixture<UserSettingsContainerComponent>;
    let comp: UserSettingsContainerComponent;

    let translateService: TranslateService;
    let accountService: AccountService;
    const router = new MockRouter();
    router.setUrl('');

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [UserSettingsContainerComponent],
            providers: [
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
        accountService = TestBed.inject(AccountService);
        translateService.currentLang = 'en';
    });
    it('should initialize with loaded user', () => {
        const getAuthenticationSpy = jest.spyOn(accountService, 'getAuthenticationState');
        fixture.detectChanges();
        expect(getAuthenticationSpy).toHaveBeenCalled();
        expect(comp.currentUser?.id).toEqual(99);
        expect(comp.isAtLeastTutor).toEqual(false);
    });
});
