import { ComponentFixture, TestBed, inject } from '@angular/core/testing';
import { FormBuilder, NgModel } from '@angular/forms';
import { of, throwError } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { PasswordResetInitComponent } from 'app/account/password-reset/init/password-reset-init.component';
import { PasswordResetInitService } from 'app/account/password-reset/init/password-reset-init.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { MockProfileService } from '../../helpers/mocks/service/mock-profile.service';
import { AlertService } from 'app/core/util/alert.service';
import { MockDirective, MockProvider } from 'ng-mocks';
import { MockTranslateService, TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { By } from '@angular/platform-browser';

describe('PasswordResetInitComponent', () => {
    let fixture: ComponentFixture<PasswordResetInitComponent>;
    let comp: PasswordResetInitComponent;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [PasswordResetInitComponent, TranslatePipeMock, MockDirective(NgModel)],
            providers: [
                FormBuilder,
                { provide: ProfileService, useClass: MockProfileService },
                MockProvider(AlertService),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: NgbModal, useClass: MockNgbModalService },
            ],
        })
            .compileComponents()
            .then(() => {
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
        jest.spyOn(service, 'save').mockReturnValue(of({}));
        comp.emailUsernameValue = 'user@domain.com';

        comp.requestReset();

        expect(service.save).toHaveBeenCalledWith('user@domain.com');
    }));

    it('no notification of success upon error response', inject([PasswordResetInitService], (service: PasswordResetInitService) => {
        jest.spyOn(service, 'save').mockReturnValue(
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

    it('no notification of success upon external user error response', inject([PasswordResetInitService], (service: PasswordResetInitService) => {
        jest.spyOn(service, 'save').mockReturnValue(
            throwError(() => ({
                status: 400,
                error: { errorKey: 'externalUser' },
            })),
        );
        comp.useExternal = true;
        comp.emailUsernameValue = 'user@domain.com';
        comp.requestReset();

        expect(service.save).toHaveBeenCalledWith('user@domain.com');
        expect(comp.externalResetModalRef).toBeDefined(); // External reference
    }));
});
