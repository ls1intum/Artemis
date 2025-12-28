/**
 * Vitest tests for EditLtiConfigurationComponent.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { FormControl, FormGroup } from '@angular/forms';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HttpClient, HttpErrorResponse, HttpResponse } from '@angular/common/http';

import { EditLtiConfigurationComponent } from 'app/core/admin/lti-configuration/edit/edit-lti-configuration.component';
import { LtiConfigurationService } from 'app/core/admin/lti-configuration/lti-configuration.service';
import { LtiPlatformConfiguration } from 'app/lti/shared/entities/lti-configuration.model';
import { AlertService } from 'app/shared/service/alert.service';

describe('Edit LTI Configuration Component', () => {
    setupTestBed({ zoneless: true });

    let comp: EditLtiConfigurationComponent;
    let fixture: ComponentFixture<EditLtiConfigurationComponent>;
    let ltiConfigurationService: LtiConfigurationService;
    let router: Router;

    const platformConfiguration = {
        id: 1,
        registrationId: 'registration_id',
        originalUrl: 'original_url',
        customName: 'custom_platform',
        clientId: 'client_id',
        authorizationUri: 'auth_uri',
        jwkSetUri: 'jwkSet_uri',
        tokenUri: 'token_uri',
    } as LtiPlatformConfiguration;

    beforeEach(async () => {
        const route = {
            snapshot: { paramMap: convertToParamMap({ platformId: '1' }) },
        } as ActivatedRoute;

        const httpClientMock = {
            get: vi.fn().mockReturnValue(of(platformConfiguration)),
        };

        const mockRouter = {
            navigate: vi.fn(),
        };

        const mockLtiConfigurationService = {
            getLtiPlatformById: vi.fn().mockReturnValue(of(platformConfiguration)),
            updateLtiPlatformConfiguration: vi.fn(),
        };

        await TestBed.configureTestingModule({
            imports: [EditLtiConfigurationComponent],
            providers: [
                { provide: LtiConfigurationService, useValue: mockLtiConfigurationService },
                { provide: Router, useValue: mockRouter },
                { provide: ActivatedRoute, useValue: route },
                { provide: HttpClient, useValue: httpClientMock },
                { provide: AlertService, useValue: { error: vi.fn() } },
            ],
        })
            .overrideTemplate(EditLtiConfigurationComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(EditLtiConfigurationComponent);
        comp = fixture.componentInstance;
        ltiConfigurationService = TestBed.inject(LtiConfigurationService);
        router = TestBed.inject(Router);
    });

    afterEach(() => {
        vi.clearAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(comp).toBeTruthy();
        expect(comp.platform).toEqual(platformConfiguration);
        expect(comp.platformConfigurationForm).toBeDefined();
        expect(comp.platformConfigurationForm.get('registrationId')?.value).toEqual(platformConfiguration.registrationId);
        expect(comp.platformConfigurationForm.get('originalUrl')?.value).toEqual(platformConfiguration.originalUrl);
        expect(comp.platformConfigurationForm.get('customName')?.value).toEqual(platformConfiguration.customName);
        expect(comp.platformConfigurationForm.get('clientId')?.value).toEqual(platformConfiguration.clientId);
        expect(comp.platformConfigurationForm.get('authorizationUri')?.value).toEqual(platformConfiguration.authorizationUri);
        expect(comp.platformConfigurationForm.get('jwkSetUri')?.value).toEqual(platformConfiguration.jwkSetUri);
        expect(comp.platformConfigurationForm.get('tokenUri')?.value).toEqual(platformConfiguration.tokenUri);
    });

    it('should save and navigate', async () => {
        fixture.detectChanges();

        const changedConfiguration = updateConfiguration();

        const updateResponse: HttpResponse<void> = new HttpResponse({
            status: 200,
        });

        vi.spyOn(ltiConfigurationService, 'updateLtiPlatformConfiguration').mockReturnValue(of(updateResponse));
        const navigateSpy = vi.spyOn(router, 'navigate');

        expect(comp.isSaving()).toBe(false);

        await comp.save();

        expect(ltiConfigurationService.updateLtiPlatformConfiguration).toHaveBeenCalledOnce();
        expect(ltiConfigurationService.updateLtiPlatformConfiguration).toHaveBeenCalledWith(changedConfiguration);
        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledWith(['admin', 'lti-configuration']);
    });

    it('should handle save failure and display error', async () => {
        fixture.detectChanges();

        const changedConfiguration = updateConfiguration();

        const errorResponse = new HttpErrorResponse({
            error: 'test error',
            status: 400,
            statusText: 'Bad Request',
        });

        vi.spyOn(ltiConfigurationService, 'updateLtiPlatformConfiguration').mockReturnValue(throwError(() => errorResponse));
        const navigateSpy = vi.spyOn(router, 'navigate');

        expect(comp.isSaving()).toBe(false);
        await comp.save();
        expect(ltiConfigurationService.updateLtiPlatformConfiguration).toHaveBeenCalledOnce();
        expect(ltiConfigurationService.updateLtiPlatformConfiguration).toHaveBeenCalledWith(changedConfiguration);

        expect(navigateSpy).not.toHaveBeenCalled();
    });

    it('should add new configuration when no existing platform', async () => {
        // Create a component with no existing platform
        comp.platform = undefined as any;
        const newConfiguration = {
            id: null, // FormControl converts undefined to null
            registrationId: 'new_registration',
            originalUrl: 'new_url',
            customName: 'new_platform',
            clientId: 'new_client_id',
            authorizationUri: 'new_auth_uri',
            tokenUri: 'new_token_uri',
            jwkSetUri: 'new_jwk_uri',
        };

        comp.platformConfigurationForm = new FormGroup({
            id: new FormControl(null),
            registrationId: new FormControl(newConfiguration.registrationId),
            originalUrl: new FormControl(newConfiguration.originalUrl),
            customName: new FormControl(newConfiguration.customName),
            clientId: new FormControl(newConfiguration.clientId),
            authorizationUri: new FormControl(newConfiguration.authorizationUri),
            tokenUri: new FormControl(newConfiguration.tokenUri),
            jwkSetUri: new FormControl(newConfiguration.jwkSetUri),
        });

        const mockLtiService = TestBed.inject(LtiConfigurationService) as any;
        mockLtiService.addLtiPlatformConfiguration = vi.fn().mockReturnValue(of(new HttpResponse({ status: 201 })));
        const navigateSpy = vi.spyOn(router, 'navigate');

        await comp.save();

        expect(mockLtiService.addLtiPlatformConfiguration).toHaveBeenCalledWith(newConfiguration);
        expect(navigateSpy).toHaveBeenCalledWith(['admin', 'lti-configuration']);
        expect(comp.isSaving()).toBe(false);
    });

    it('should handle add configuration failure', async () => {
        comp.platform = undefined as any;
        const alertService = TestBed.inject(AlertService);

        comp.platformConfigurationForm = new FormGroup({
            id: new FormControl(undefined),
            registrationId: new FormControl('test'),
            originalUrl: new FormControl('url'),
            customName: new FormControl('name'),
            clientId: new FormControl('client'),
            authorizationUri: new FormControl('auth'),
            tokenUri: new FormControl('token'),
            jwkSetUri: new FormControl('jwk'),
        });

        const errorResponse = new HttpErrorResponse({ status: 500, statusText: 'Server Error' });
        const mockLtiService = TestBed.inject(LtiConfigurationService) as any;
        mockLtiService.addLtiPlatformConfiguration = vi.fn().mockReturnValue(throwError(() => errorResponse));

        await comp.save();

        expect(alertService.error).toHaveBeenCalled();
        expect(comp.isSaving()).toBe(false);
    });

    it('should handle error when loading platform by id fails', async () => {
        const alertService = TestBed.inject(AlertService);
        const errorResponse = new HttpErrorResponse({ status: 404, statusText: 'Not Found' });
        vi.spyOn(ltiConfigurationService, 'getLtiPlatformById').mockReturnValue(throwError(() => errorResponse));

        comp.ngOnInit();

        expect(alertService.error).toHaveBeenCalled();
    });

    it('should initialize form without platform when no platformId in route', async () => {
        // Reset the route to have no platformId
        const routeWithoutId = {
            snapshot: { paramMap: convertToParamMap({}) },
        } as ActivatedRoute;

        await TestBed.resetTestingModule();
        await TestBed.configureTestingModule({
            imports: [EditLtiConfigurationComponent],
            providers: [
                { provide: LtiConfigurationService, useValue: { getLtiPlatformById: vi.fn() } },
                { provide: Router, useValue: { navigate: vi.fn() } },
                { provide: ActivatedRoute, useValue: routeWithoutId },
                { provide: HttpClient, useValue: { get: vi.fn() } },
                { provide: AlertService, useValue: { error: vi.fn() } },
            ],
        })
            .overrideTemplate(EditLtiConfigurationComponent, '')
            .compileComponents();

        const newFixture = TestBed.createComponent(EditLtiConfigurationComponent);
        const newComp = newFixture.componentInstance;

        newComp.ngOnInit();

        expect(newComp.platformConfigurationForm).toBeDefined();
        expect(newComp.platform).toBeUndefined();
    });

    it('should navigate to lti configuration page', () => {
        const navigateSpy = vi.spyOn(router, 'navigate');

        comp.navigateToLtiConfigurationPage();

        expect(navigateSpy).toHaveBeenCalledWith(['admin', 'lti-configuration']);
    });

    function updateConfiguration() {
        const changedConfiguration = {
            ...platformConfiguration,
            customName: 'custom_name_2',
        } as LtiPlatformConfiguration;

        comp.platformConfigurationForm = new FormGroup({
            id: new FormControl(changedConfiguration.id),
            registrationId: new FormControl(changedConfiguration.registrationId),
            originalUrl: new FormControl(changedConfiguration.originalUrl),
            customName: new FormControl(changedConfiguration.customName),
            clientId: new FormControl(changedConfiguration.clientId),
            authorizationUri: new FormControl(changedConfiguration.authorizationUri),
            tokenUri: new FormControl(changedConfiguration.tokenUri),
            jwkSetUri: new FormControl(changedConfiguration.jwkSetUri),
        });
        return changedConfiguration;
    }
});
