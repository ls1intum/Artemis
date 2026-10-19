import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { MockPipe, MockProvider } from 'ng-mocks';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { TranslateService } from '@ngx-translate/core';
import { HttpResponse } from '@angular/common/http';

import { EditLtiConfigurationComponent } from './edit-lti-configuration.component';
import { LtiConfigurationService } from 'app/admin/lti-configuration/lti-configuration.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { LtiPlatformConfiguration } from 'app/lti/shared/entities/lti-configuration.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { LTI_PLATFORM_CUSTOM_NAME_MAX_LENGTH, LTI_PLATFORM_FIELD_MAX_LENGTH } from 'app/foundation/constants/input.constants';

describe('EditLtiConfigurationComponent', () => {
    let component: EditLtiConfigurationComponent;
    let fixture: ComponentFixture<EditLtiConfigurationComponent>;
    let ltiConfigurationService: LtiConfigurationService;
    let alertService: AlertService;
    let router: Router;

    const mockPlatform: LtiPlatformConfiguration = {
        id: 1,
        registrationId: 'test-registration-id',
        clientId: 'test-client-id',
        authorizationUri: 'https://example.com/auth',
        tokenUri: 'https://example.com/token',
        jwkSetUri: 'https://example.com/jwks',
        customName: 'Test Platform',
        originalUrl: 'https://example.com',
    };

    const createActivatedRouteMock = (platformId: string | null) => ({
        snapshot: {
            paramMap: {
                get: vi.fn().mockReturnValue(platformId),
            },
        },
    });

    describe('Create Mode', () => {
        beforeEach(async () => {
            await TestBed.configureTestingModule({
                imports: [ReactiveFormsModule, FormsModule, FontAwesomeTestingModule, EditLtiConfigurationComponent, MockPipe(ArtemisTranslatePipe)],
                providers: [
                    MockProvider(LtiConfigurationService),
                    MockProvider(AlertService),
                    MockProvider(Router),
                    { provide: TranslateService, useClass: MockTranslateService },
                    {
                        provide: ActivatedRoute,
                        useValue: createActivatedRouteMock(null),
                    },
                ],
            }).compileComponents();

            fixture = TestBed.createComponent(EditLtiConfigurationComponent);
            component = fixture.componentInstance;
            ltiConfigurationService = TestBed.inject(LtiConfigurationService);
            alertService = TestBed.inject(AlertService);
            router = TestBed.inject(Router);
        });

        afterEach(() => {
            vi.restoreAllMocks();
        });

        it('should initialize the form with empty values in create mode', () => {
            fixture.detectChanges();

            expect(component.platformConfigurationForm).toBeDefined();
            expect(component.platformConfigurationForm.get('clientId')?.value).toBe('');
            expect(component.platformConfigurationForm.get('authorizationUri')?.value).toBe('');
            expect(component.platformConfigurationForm.get('tokenUri')?.value).toBe('');
            expect(component.platformConfigurationForm.get('jwkSetUri')?.value).toBe('');
            expect(component.isEditMode()).toBe(false);
        });

        it('should have invalid form when required fields are empty', () => {
            fixture.detectChanges();

            expect(component.platformConfigurationForm.invalid).toBe(true);
        });

        it('should have valid form when all required fields are filled', () => {
            fixture.detectChanges();

            component.platformConfigurationForm.patchValue({
                customName: 'Test platform',
                clientId: 'test-client',
                authorizationUri: 'https://example.com/auth',
                tokenUri: 'https://example.com/token',
                jwkSetUri: 'https://example.com/jwks',
            });

            expect(component.platformConfigurationForm.valid).toBe(true);
        });

        it('should mark customName as required', () => {
            fixture.detectChanges();

            const customNameControl = component.platformConfigurationForm.get('customName');
            expect(customNameControl?.errors?.['required']).toBe(true);

            customNameControl?.setValue('   ');
            expect(customNameControl?.invalid).toBe(true);

            customNameControl?.setValue('Moodle');
            expect(customNameControl?.errors).toBeNull();
        });

        it('should reject a custom name that is longer than the server accepts', () => {
            fixture.detectChanges();

            const customNameControl = component.platformConfigurationForm.get('customName');
            customNameControl?.setValue('n'.repeat(LTI_PLATFORM_CUSTOM_NAME_MAX_LENGTH));
            expect(customNameControl?.errors).toBeNull();

            customNameControl?.setValue('n'.repeat(LTI_PLATFORM_CUSTOM_NAME_MAX_LENGTH + 1));
            expect(customNameControl?.errors?.['maxlength']).toBeDefined();
        });

        it('should show the custom name error only once the field was touched', () => {
            fixture.detectChanges();
            const element: HTMLElement = fixture.nativeElement;
            expect(element.querySelector('[jhiTranslate="artemisApp.lti.validation.customNameRequired"]')).toBeNull();

            component.platformConfigurationForm.get('customName')?.markAsTouched();
            fixture.detectChanges();

            expect(element.querySelector('[jhiTranslate="artemisApp.lti.validation.customNameRequired"]')).not.toBeNull();
        });

        it('should limit the length of the inputs to what the server accepts', () => {
            fixture.detectChanges();
            const element: HTMLElement = fixture.nativeElement;

            expect(element.querySelector('#field_customName')?.getAttribute('maxlength')).toBe(String(LTI_PLATFORM_CUSTOM_NAME_MAX_LENGTH));
            for (const id of ['field_toolUrl', 'field_clientId', 'field_authorizationUri', 'field_tokenUri', 'field_jwkSetUri']) {
                expect(element.querySelector(`#${id}`)?.getAttribute('maxlength'), id).toBe(String(LTI_PLATFORM_FIELD_MAX_LENGTH));
            }
        });

        it('should mark clientId as required', () => {
            fixture.detectChanges();

            const clientIdControl = component.platformConfigurationForm.get('clientId');
            expect(clientIdControl?.errors?.['required']).toBe(true);

            clientIdControl?.setValue('test-client');
            expect(clientIdControl?.errors).toBeNull();
        });

        it('should mark authorizationUri as required', () => {
            fixture.detectChanges();

            const authorizationUriControl = component.platformConfigurationForm.get('authorizationUri');
            expect(authorizationUriControl?.errors?.['required']).toBe(true);

            authorizationUriControl?.setValue('https://example.com/auth');
            expect(authorizationUriControl?.errors).toBeNull();
        });

        it('should mark tokenUri as required', () => {
            fixture.detectChanges();

            const tokenUriControl = component.platformConfigurationForm.get('tokenUri');
            expect(tokenUriControl?.errors?.['required']).toBe(true);

            tokenUriControl?.setValue('https://example.com/token');
            expect(tokenUriControl?.errors).toBeNull();
        });

        it('should mark jwkSetUri as required', () => {
            fixture.detectChanges();

            const jwkSetUriControl = component.platformConfigurationForm.get('jwkSetUri');
            expect(jwkSetUriControl?.errors?.['required']).toBe(true);

            jwkSetUriControl?.setValue('https://example.com/jwks');
            expect(jwkSetUriControl?.errors).toBeNull();
        });

        describe('isFieldInvalid', () => {
            it('should return false for untouched invalid field', () => {
                fixture.detectChanges();

                expect(component.isFieldInvalid('clientId')).toBe(false);
            });

            it('should return true for touched invalid field', () => {
                fixture.detectChanges();

                const clientIdControl = component.platformConfigurationForm.get('clientId');
                clientIdControl?.markAsTouched();

                expect(component.isFieldInvalid('clientId')).toBe(true);
            });

            it('should return true for dirty invalid field', () => {
                fixture.detectChanges();

                const clientIdControl = component.platformConfigurationForm.get('clientId');
                clientIdControl?.markAsDirty();

                expect(component.isFieldInvalid('clientId')).toBe(true);
            });

            it('should return false for valid field', () => {
                fixture.detectChanges();

                const clientIdControl = component.platformConfigurationForm.get('clientId');
                clientIdControl?.setValue('test-client');
                clientIdControl?.markAsTouched();

                expect(component.isFieldInvalid('clientId')).toBe(false);
            });
        });

        describe('Save in create mode', () => {
            it('should call addLtiConfiguration when creating new platform', () => {
                vi.spyOn(ltiConfigurationService, 'addLtiPlatformConfiguration').mockReturnValue(of(new HttpResponse({ body: mockPlatform })));
                const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

                fixture.detectChanges();

                component.platformConfigurationForm.patchValue({
                    customName: 'Test platform',
                    clientId: 'test-client',
                    authorizationUri: 'https://example.com/auth',
                    tokenUri: 'https://example.com/token',
                    jwkSetUri: 'https://example.com/jwks',
                });

                component.save();

                expect(ltiConfigurationService.addLtiPlatformConfiguration).toHaveBeenCalled();
                expect(navigateSpy).toHaveBeenCalledWith(['admin', 'lti-configuration']);
            });

            it('should show error alert when save fails', () => {
                vi.spyOn(ltiConfigurationService, 'addLtiPlatformConfiguration').mockReturnValue(throwError(() => new Error('Save failed')));
                const alertErrorSpy = vi.spyOn(alertService, 'error');

                fixture.detectChanges();

                component.platformConfigurationForm.patchValue({
                    customName: 'Test platform',
                    clientId: 'test-client',
                    authorizationUri: 'https://example.com/auth',
                    tokenUri: 'https://example.com/token',
                    jwkSetUri: 'https://example.com/jwks',
                });

                component.save();

                expect(alertErrorSpy).toHaveBeenCalled();
                expect(component.isSaving()).toBe(false);
            });
        });

        describe('Navigation', () => {
            it('should navigate to lti-configuration page', () => {
                fixture.detectChanges();

                const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

                component.navigateToLtiConfigurationPage();

                expect(navigateSpy).toHaveBeenCalledWith(['admin', 'lti-configuration']);
            });
        });
    });

    describe('Edit Mode - Success', () => {
        beforeEach(async () => {
            await TestBed.configureTestingModule({
                imports: [ReactiveFormsModule, FormsModule, FontAwesomeTestingModule, EditLtiConfigurationComponent, MockPipe(ArtemisTranslatePipe)],
                providers: [
                    {
                        provide: LtiConfigurationService,
                        useValue: {
                            getLtiPlatformById: vi.fn().mockReturnValue(of(mockPlatform)),
                            updateLtiPlatformConfiguration: vi.fn().mockReturnValue(of(new HttpResponse({ body: mockPlatform }))),
                        },
                    },
                    MockProvider(AlertService),
                    MockProvider(Router),
                    { provide: TranslateService, useClass: MockTranslateService },
                    {
                        provide: ActivatedRoute,
                        useValue: createActivatedRouteMock('1'),
                    },
                ],
            }).compileComponents();

            fixture = TestBed.createComponent(EditLtiConfigurationComponent);
            component = fixture.componentInstance;
            ltiConfigurationService = TestBed.inject(LtiConfigurationService);
            alertService = TestBed.inject(AlertService);
            router = TestBed.inject(Router);
        });

        afterEach(() => {
            vi.restoreAllMocks();
        });

        it('should set isEditMode and load data when platformId is provided', () => {
            fixture.detectChanges();

            expect(component.isEditMode()).toBe(true);
            expect(ltiConfigurationService.getLtiPlatformById).toHaveBeenCalledWith(1);
            expect(component.platform()).toEqual(mockPlatform);
            expect(component.platformConfigurationForm.get('clientId')?.value).toBe(mockPlatform.clientId);
            expect(component.platformConfigurationForm.get('authorizationUri')?.value).toBe(mockPlatform.authorizationUri);
        });

        it('should call updateLtiConfiguration when editing existing platform', () => {
            const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

            fixture.detectChanges();

            component.platformConfigurationForm.patchValue({
                clientId: 'updated-client',
            });

            component.save();

            expect(ltiConfigurationService.updateLtiPlatformConfiguration).toHaveBeenCalled();
            expect(navigateSpy).toHaveBeenCalledWith(['admin', 'lti-configuration']);
        });
    });

    describe('Edit Mode - Load Failed', () => {
        beforeEach(async () => {
            await TestBed.configureTestingModule({
                imports: [ReactiveFormsModule, FormsModule, FontAwesomeTestingModule, EditLtiConfigurationComponent, MockPipe(ArtemisTranslatePipe)],
                providers: [
                    {
                        provide: LtiConfigurationService,
                        useValue: {
                            getLtiPlatformById: vi.fn().mockReturnValue(throwError(() => new Error('Load failed'))),
                        },
                    },
                    MockProvider(AlertService),
                    MockProvider(Router),
                    { provide: TranslateService, useClass: MockTranslateService },
                    {
                        provide: ActivatedRoute,
                        useValue: createActivatedRouteMock('1'),
                    },
                ],
            }).compileComponents();

            fixture = TestBed.createComponent(EditLtiConfigurationComponent);
            component = fixture.componentInstance;
            ltiConfigurationService = TestBed.inject(LtiConfigurationService);
            alertService = TestBed.inject(AlertService);
            router = TestBed.inject(Router);
        });

        afterEach(() => {
            vi.restoreAllMocks();
        });

        it('should set loadFailed when loading platform data fails', () => {
            const alertErrorSpy = vi.spyOn(alertService, 'error');

            fixture.detectChanges();

            expect(component.isEditMode()).toBe(true);
            expect(component.loadFailed()).toBe(true);
            expect(alertErrorSpy).toHaveBeenCalled();
        });

        it('should not save when in edit mode and load failed', () => {
            const alertErrorSpy = vi.spyOn(alertService, 'error');

            fixture.detectChanges();

            // Clear previous error call from load failure
            alertErrorSpy.mockClear();

            component.save();

            expect(alertErrorSpy).toHaveBeenCalledWith('artemisApp.lti.editConfiguration.loadError');
            expect(component.isSaving()).toBe(false);
        });
    });
});
