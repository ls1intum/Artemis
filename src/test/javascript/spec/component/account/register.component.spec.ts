import { ComponentFixture, fakeAsync, inject, TestBed, tick } from '@angular/core/testing';
import { FormBuilder } from '@angular/forms';
import { of, throwError } from 'rxjs';

import { ArtemisTestModule } from '../../test.module';
import { EMAIL_ALREADY_USED_TYPE, LOGIN_ALREADY_USED_TYPE } from 'app/shared/constants/error.constants';
import { RegisterService } from 'app/account/register/register.service';
import { RegisterComponent } from 'app/account/register/register.component';
import { User } from 'app/core/user/user.model';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { MockProfileService } from '../../helpers/mocks/service/mock-profile.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('Component Tests', () => {
    describe('RegisterComponent', () => {
        let fixture: ComponentFixture<RegisterComponent>;
        let comp: RegisterComponent;
        let translateService: TranslateService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArtemisTestModule],
                declarations: [RegisterComponent],
                providers: [
                    FormBuilder,
                    { provide: LocalStorageService, useClass: MockSyncStorage },
                    { provide: SessionStorageService, useClass: MockSyncStorage },
                    { provide: ProfileService, useClass: MockProfileService },
                    { provide: TranslateService, useClass: MockTranslateService },
                ],
            })
                .overrideTemplate(RegisterComponent, '')
                .compileComponents();
        });

        beforeEach(() => {
            fixture = TestBed.createComponent(RegisterComponent);
            translateService = TestBed.inject(TranslateService);
            comp = fixture.componentInstance;
            comp.isRegistrationEnabled = true;
            translateService.currentLang = 'en';
        });

        it('should ensure the two passwords entered match', () => {
            comp.registerForm.patchValue({
                password: 'password',
                confirmPassword: 'non-matching',
            });

            comp.register();

            expect(comp.doNotMatch).toBeTrue();
        });

        it('should update success to true after creating an account', inject(
            [RegisterService],
            fakeAsync((service: RegisterService) => {
                jest.spyOn(service, 'save').mockReturnValue(of({} as any));
                comp.registerForm.patchValue({
                    password: 'password',
                    confirmPassword: 'password',
                });

                comp.register();
                tick();
                const user = new User();
                user.email = '';
                user.firstName = '';
                user.lastName = '';
                user.password = 'password';
                user.login = '';
                user.langKey = 'en';
                expect(service.save).toHaveBeenCalledWith(user);
                expect(comp.success).toBeTrue();
                expect(comp.errorUserExists).toBeFalse();
                expect(comp.errorEmailExists).toBeFalse();
                expect(comp.error).toBeFalse();
            }),
        ));

        it('should notify of user existence upon 400/login already in use', inject(
            [RegisterService],
            fakeAsync((service: RegisterService) => {
                jest.spyOn(service, 'save').mockReturnValue(
                    throwError(() => ({
                        status: 400,
                        error: { type: LOGIN_ALREADY_USED_TYPE },
                    })),
                );
                comp.registerForm.patchValue({
                    password: 'password',
                    confirmPassword: 'password',
                });

                comp.register();
                tick();

                expect(comp.errorUserExists).toBeTrue();
                expect(comp.errorEmailExists).toBeFalse();
                expect(comp.error).toBeFalse();
            }),
        ));

        it('should notify of email existence upon 400/email address already in use', inject(
            [RegisterService],
            fakeAsync((service: RegisterService) => {
                jest.spyOn(service, 'save').mockReturnValue(
                    throwError(() => ({
                        status: 400,
                        error: { type: EMAIL_ALREADY_USED_TYPE },
                    })),
                );
                comp.registerForm.patchValue({
                    password: 'password',
                    confirmPassword: 'password',
                });

                comp.register();
                tick();

                expect(comp.errorEmailExists).toBeTrue();
                expect(comp.errorUserExists).toBeFalse();
                expect(comp.error).toBeFalse();
            }),
        ));

        it('should notify of generic error', inject(
            [RegisterService],
            fakeAsync((service: RegisterService) => {
                jest.spyOn(service, 'save').mockReturnValue(
                    throwError(() => ({
                        status: 503,
                    })),
                );
                comp.registerForm.patchValue({
                    password: 'password',
                    confirmPassword: 'password',
                });

                comp.register();
                tick();

                expect(comp.errorUserExists).toBeFalse();
                expect(comp.errorEmailExists).toBeFalse();
                expect(comp.error).toBeTrue();
            }),
        ));
    });
});
