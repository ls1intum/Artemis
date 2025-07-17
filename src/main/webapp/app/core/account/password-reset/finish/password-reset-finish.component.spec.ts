import { ElementRef } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, inject, tick } from '@angular/core/testing';
import { FormBuilder } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { LocalStorageService } from 'app/shared/storage/local-storage.service';
import { SessionStorageService } from 'app/shared/storage/session-storage.service';
import { of, throwError } from 'rxjs';

import { PasswordResetFinishComponent } from 'app/core/account/password-reset/finish/password-reset-finish.component';
import { PasswordResetFinishService } from 'app/core/account/password-reset/finish/password-reset-finish.service';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { provideHttpClient } from '@angular/common/http';

describe('Component Tests', () => {
    describe('PasswordResetFinishComponent', () => {
        let fixture: ComponentFixture<PasswordResetFinishComponent>;
        let comp: PasswordResetFinishComponent;

        beforeEach(() => {
            fixture = TestBed.configureTestingModule({
                imports: [PasswordResetFinishComponent],
                providers: [
                    FormBuilder,
                    {
                        provide: ActivatedRoute,
                        useValue: new MockActivatedRoute({ key: 'XYZPDQ' }),
                    },
                    LocalStorageService,
                    SessionStorageService,
                    { provide: ProfileService, useClass: MockProfileService },
                    provideHttpClient(),
                ],
            })
                .overrideTemplate(PasswordResetFinishComponent, '')
                .createComponent(PasswordResetFinishComponent);
        });

        beforeEach(() => {
            fixture = TestBed.createComponent(PasswordResetFinishComponent);
            comp = fixture.componentInstance;
            comp.ngOnInit();
        });

        it('should define its initial state', () => {
            expect(comp.initialized).toBeTrue();
            expect(comp.key).toBe('XYZPDQ');
        });

        it('sets focus after the view has been initialized', () => {
            const node = {
                focus(): void {},
            };
            comp.newPassword = new ElementRef(node);
            jest.spyOn(node, 'focus');

            comp.ngAfterViewInit();

            expect(node.focus).toHaveBeenCalledOnce();
        });

        it('should ensure the two passwords entered match', () => {
            comp.passwordForm.patchValue({
                newPassword: 'password',
                confirmPassword: 'non-matching',
            });

            comp.finishReset();

            expect(comp.doNotMatch).toBeTrue();
        });

        it('should update success to true after resetting password', inject(
            [PasswordResetFinishService],
            fakeAsync((service: PasswordResetFinishService) => {
                jest.spyOn(service, 'save').mockReturnValue(of({}));
                comp.passwordForm.patchValue({
                    newPassword: 'password',
                    confirmPassword: 'password',
                });

                comp.finishReset();
                tick();

                expect(service.save).toHaveBeenCalledWith('XYZPDQ', 'password');
                expect(comp.success).toBeTrue();
            }),
        ));

        it('should notify of generic error', inject(
            [PasswordResetFinishService],
            fakeAsync((service: PasswordResetFinishService) => {
                jest.spyOn(service, 'save').mockReturnValue(throwError(() => new Error('ERROR')));
                comp.passwordForm.patchValue({
                    newPassword: 'password',
                    confirmPassword: 'password',
                });

                comp.finishReset();
                tick();

                expect(service.save).toHaveBeenCalledWith('XYZPDQ', 'password');
                expect(comp.success).toBeFalse();
                expect(comp.error).toBeTrue();
            }),
        ));
    });
});
