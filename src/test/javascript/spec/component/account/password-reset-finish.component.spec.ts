import { ElementRef } from '@angular/core';
import { ComponentFixture, fakeAsync, inject, TestBed, tick } from '@angular/core/testing';
import { FormBuilder } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { of, throwError } from 'rxjs';

import { ArtemisTestModule } from '../../test.module';
import { PasswordResetFinishComponent } from 'app/account/password-reset/finish/password-reset-finish.component';
import { PasswordResetFinishService } from 'app/account/password-reset/finish/password-reset-finish.service';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { MockProfileService } from '../../helpers/mocks/service/mock-profile.service';

describe('Component Tests', () => {
    describe('PasswordResetFinishComponent', () => {
        let fixture: ComponentFixture<PasswordResetFinishComponent>;
        let comp: PasswordResetFinishComponent;

        beforeEach(() => {
            fixture = TestBed.configureTestingModule({
                imports: [ArtemisTestModule],
                declarations: [PasswordResetFinishComponent],
                providers: [
                    FormBuilder,
                    {
                        provide: ActivatedRoute,
                        useValue: new MockActivatedRoute({ key: 'XYZPDQ' }),
                    },
                    { provide: LocalStorageService, useClass: MockSyncStorage },
                    { provide: SessionStorageService, useClass: MockSyncStorage },
                    { provide: ProfileService, useClass: MockProfileService },
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
            expect(comp.key).toEqual('XYZPDQ');
        });

        it('sets focus after the view has been initialized', () => {
            const node = {
                focus(): void {},
            };
            comp.newPassword = new ElementRef(node);
            jest.spyOn(node, 'focus');

            comp.ngAfterViewInit();

            expect(node.focus).toHaveBeenCalled();
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
