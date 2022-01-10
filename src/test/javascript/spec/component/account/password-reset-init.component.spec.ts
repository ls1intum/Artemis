import { ElementRef } from '@angular/core';
import { ComponentFixture, TestBed, inject } from '@angular/core/testing';
import { FormBuilder } from '@angular/forms';
import { of, throwError } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { PasswordResetInitComponent } from 'app/account/password-reset/init/password-reset-init.component';
import { PasswordResetInitService } from 'app/account/password-reset/init/password-reset-init.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { MockProfileService } from '../../helpers/mocks/service/mock-profile.service';
import { AlertService } from 'app/core/util/alert.service';
import { MockComponent, MockProvider } from 'ng-mocks';
import { MockTranslateService, TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertComponent } from 'app/shared/alert/alert.component';

describe('PasswordResetInitComponent', () => {
    let fixture: ComponentFixture<PasswordResetInitComponent>;
    let comp: PasswordResetInitComponent;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [PasswordResetInitComponent, TranslatePipeMock, MockComponent(AlertComponent)],
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
                comp.passwordResetEnabled = true;
            });
    });

    it('sets focus after the view has been initialized', () => {
        const node = {
            focus(): void {},
        };
        comp.email = new ElementRef(node);
        jest.spyOn(node, 'focus');

        comp.ngAfterViewInit();

        expect(node.focus).toHaveBeenCalled();
    });

    it('notifies of success upon successful requestReset', inject([PasswordResetInitService], (service: PasswordResetInitService) => {
        jest.spyOn(service, 'save').mockReturnValue(of({}));
        comp.resetRequestForm.patchValue({
            email: 'user@domain.com',
        });

        comp.requestReset();

        expect(service.save).toHaveBeenCalledWith('user@domain.com');
        expect(comp.success).toBe(true);
    }));

    it('no notification of success upon error response', inject([PasswordResetInitService], (service: PasswordResetInitService) => {
        jest.spyOn(service, 'save').mockReturnValue(
            throwError(() => ({
                status: 503,
                data: 'something else',
            })),
        );
        comp.resetRequestForm.patchValue({
            email: 'user@domain.com',
        });
        comp.requestReset();

        expect(service.save).toHaveBeenCalledWith('user@domain.com');
        expect(comp.success).toBe(false);
    }));
});
