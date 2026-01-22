import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { MockPipe, MockProvider } from 'ng-mocks';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { TranslateService } from '@ngx-translate/core';

import { EditLtiConfigurationComponent } from './edit-lti-configuration.component';
import { LtiConfigurationService } from 'app/core/admin/lti-configuration/lti-configuration.service';
import { AlertService } from 'app/shared/service/alert.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { LtiPlatformConfiguration } from 'app/lti/shared/entities/lti-configuration.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('EditLtiConfigurationComponent', () => {
    setupTestBed({ zoneless: true });

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
                clientId: 'test-client',
                authorizationUri: 'https://example.com/auth',
                tokenUri: 'https://example.com/token',
                jwkSetUri: 'https://example.com/jwks',
            });

            expect(component.platformConfigurationForm.valid).toBe(true);
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
                vi.spyOn(ltiConfigurationService, 'addLtiPlatformConfiguration').mockReturnValue(of(mockPlatform));
                const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

                fixture.detectChanges();

                component.platformConfigurationForm.patchValue({
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
                            updateLtiPlatformConfiguration: vi.fn().mockReturnValue(of(mockPlatform)),
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
            expect(component.platform).toEqual(mockPlatform);
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
