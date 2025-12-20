/**
 * Vitest tests for PasswordResetFinishComponent.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ElementRef } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { FormBuilder } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { of, throwError } from 'rxjs';

import { PasswordResetFinishComponent } from 'app/core/account/password-reset/finish/password-reset-finish.component';
import { PasswordResetFinishService } from 'app/core/account/password-reset/finish/password-reset-finish.service';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { provideHttpClient } from '@angular/common/http';

describe('Component Tests', () => {
    describe('PasswordResetFinishComponent', () => {
        setupTestBed({ zoneless: true });

        let fixture: ComponentFixture<PasswordResetFinishComponent>;
        let comp: PasswordResetFinishComponent;
        let passwordResetFinishService: PasswordResetFinishService;

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
            passwordResetFinishService = TestBed.inject(PasswordResetFinishService);
            comp.ngOnInit();
        });

        it('should define its initial state', () => {
            expect(comp.initialized()).toBe(true);
            expect(comp.key()).toBe('XYZPDQ');
        });

        it('sets focus after the view has been initialized', () => {
            const mockElement = document.createElement('input');
            const focusSpy = vi.spyOn(mockElement, 'focus');

            // Mock the viewChild signal to return the element
            vi.spyOn(comp, 'newPassword').mockReturnValue({ nativeElement: mockElement } as ElementRef);

            comp.ngAfterViewInit();

            expect(focusSpy).toHaveBeenCalledOnce();
        });

        it('should ensure the two passwords entered match', () => {
            comp.passwordForm.patchValue({
                newPassword: 'password',
                confirmPassword: 'non-matching',
            });

            comp.finishReset();

            expect(comp.doNotMatch()).toBe(true);
        });

        it('should update success to true after resetting password', () => {
            vi.spyOn(passwordResetFinishService, 'save').mockReturnValue(of({}));
            comp.passwordForm.patchValue({
                newPassword: 'password',
                confirmPassword: 'password',
            });

            comp.finishReset();

            expect(passwordResetFinishService.save).toHaveBeenCalledWith('XYZPDQ', 'password');
            expect(comp.success()).toBe(true);
        });

        it('should notify of generic error', () => {
            vi.spyOn(passwordResetFinishService, 'save').mockReturnValue(throwError(() => new Error('ERROR')));
            comp.passwordForm.patchValue({
                newPassword: 'password',
                confirmPassword: 'password',
            });

            comp.finishReset();

            expect(passwordResetFinishService.save).toHaveBeenCalledWith('XYZPDQ', 'password');
            expect(comp.success()).toBe(false);
            expect(comp.error()).toBe(true);
        });
    });
});
