import { ElementRef } from '@angular/core';
import { ComponentFixture, TestBed, inject } from '@angular/core/testing';
import { FormBuilder } from '@angular/forms';
import { of, throwError } from 'rxjs';

import { ArtemisTestModule } from '../../test.module';
import { PasswordResetInitComponent } from 'app/account/password-reset/init/password-reset-init.component';
import { PasswordResetInitService } from 'app/account/password-reset/init/password-reset-init.service';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { MockProfileService } from '../../helpers/mocks/service/mock-profile.service';

describe('Component Tests', () => {
    describe('PasswordResetInitComponent', () => {
        let fixture: ComponentFixture<PasswordResetInitComponent>;
        let comp: PasswordResetInitComponent;

        beforeEach(() => {
            fixture = TestBed.configureTestingModule({
                imports: [ArtemisTestModule],
                declarations: [PasswordResetInitComponent],
                providers: [
                    FormBuilder,
                    { provide: LocalStorageService, useClass: MockSyncStorage },
                    { provide: SessionStorageService, useClass: MockSyncStorage },
                    { provide: ProfileService, useClass: MockProfileService },
                ],
            })
                .overrideTemplate(PasswordResetInitComponent, '')
                .createComponent(PasswordResetInitComponent);
            comp = fixture.componentInstance;
            comp.passwordResetEnabled = true;
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
});
