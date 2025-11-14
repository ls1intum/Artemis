import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { LoginWithPasskeyModalComponent } from './login-with-passkey-modal.component';
import { AccountService } from 'app/core/auth/account.service';
import { WebauthnService } from 'app/core/user/settings/passkey-settings/webauthn.service';
import { AlertService } from 'app/shared/service/alert.service';
import { EventManager } from 'app/shared/service/event-manager.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('LoginWithPasskeyModal', () => {
    let component: LoginWithPasskeyModalComponent;
    let fixture: ComponentFixture<LoginWithPasskeyModalComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LoginWithPasskeyModalComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                provideRouter([]),
                { provide: AccountService, useClass: MockAccountService },
                { provide: AlertService, useClass: MockAlertService },
                { provide: TranslateService, useClass: MockTranslateService },
                EventManager,
                WebauthnService,
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(LoginWithPasskeyModalComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
