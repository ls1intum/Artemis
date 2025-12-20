/**
 * Vitest tests for PasswordResetInitComponent.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed, inject } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { FormBuilder } from '@angular/forms';
import { of, throwError } from 'rxjs';
import { PasswordResetInitComponent } from 'app/core/account/password-reset/init/password-reset-init.component';
import { PasswordResetInitService } from 'app/core/account/password-reset/init/password-reset-init.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { AlertService } from 'app/shared/service/alert.service';
import { MockProvider } from 'ng-mocks';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { By } from '@angular/platform-browser';
import { provideHttpClient } from '@angular/common/http';

describe('PasswordResetInitComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<PasswordResetInitComponent>;
    let comp: PasswordResetInitComponent;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [PasswordResetInitComponent],
            providers: [
                FormBuilder,
                ProfileService,
                MockProvider(AlertService),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: NgbModal, useClass: MockNgbModalService },
                provideHttpClient(),
            ],
        })
            .compileComponents()
            .then(() => {
                // Mock ProfileService before component creation
                const profileService = TestBed.inject(ProfileService);
                vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({
                    useExternal: false,
                    externalCredentialProvider: '',
                    externalPasswordResetLinkMap: {},
                } as any);

                fixture = TestBed.createComponent(PasswordResetInitComponent);
                comp = fixture.componentInstance;
            });
    });

    it('sets focus after the view has been initialized', () => {
        fixture.detectChanges();

        const emailUsernameInput = fixture.debugElement.query(By.css('#emailUsername')).nativeElement;
        const focusedElement = fixture.debugElement.query(By.css(':focus')).nativeElement;
        expect(focusedElement).toBe(emailUsernameInput);
    });

    it('notifies of success upon successful requestReset', inject([PasswordResetInitService], (service: PasswordResetInitService) => {
        vi.spyOn(service, 'save').mockReturnValue(of({}));
        comp.emailUsernameValue = 'user@domain.com';

        comp.requestReset();

        expect(service.save).toHaveBeenCalledWith('user@domain.com');
    }));

    it('no notification of success upon error response', inject([PasswordResetInitService], (service: PasswordResetInitService) => {
        vi.spyOn(service, 'save').mockReturnValue(
            throwError(() => ({
                status: 503,
                data: 'something else',
            })),
        );
        comp.emailUsernameValue = 'user@domain.com';

        comp.requestReset();

        expect(service.save).toHaveBeenCalledWith('user@domain.com');
        expect(comp.externalResetModalRef).toBeUndefined();
    }));

    it('opens external reset modal upon external user error response', () => {
        // Need to recreate component with useExternal: true
        const profileService = TestBed.inject(ProfileService);
        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({ useExternal: true, externalCredentialProvider: 'LDAP' } as any);

        // Recreate the component with the new profile info
        fixture = TestBed.createComponent(PasswordResetInitComponent);
        comp = fixture.componentInstance;

        const service = TestBed.inject(PasswordResetInitService);
        vi.spyOn(service, 'save').mockReturnValue(
            throwError(() => ({
                status: 400,
                error: { errorKey: 'externalUser' },
            })),
        );
        comp.emailUsernameValue = 'user@domain.com';
        comp.requestReset();

        expect(service.save).toHaveBeenCalledWith('user@domain.com');
        expect(comp.externalResetModalRef).toBeDefined(); // External reference
    });

    it('shows error alert when emailUsernameValue is empty', inject([AlertService], (alertService: AlertService) => {
        const errorSpy = vi.spyOn(alertService, 'error');
        comp.emailUsernameValue = '';

        comp.requestReset();

        expect(errorSpy).toHaveBeenCalledWith('reset.request.messages.info');
    }));

    it('shows error alert when emailUsernameValue is undefined', inject([AlertService], (alertService: AlertService) => {
        const errorSpy = vi.spyOn(alertService, 'error');
        comp.emailUsernameValue = undefined as any;

        comp.requestReset();

        expect(errorSpy).toHaveBeenCalledWith('reset.request.messages.info');
    }));
});
