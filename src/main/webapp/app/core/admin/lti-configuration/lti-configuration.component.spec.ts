/**
 * Vitest tests for LtiConfigurationComponent.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';

import { LtiConfigurationService } from 'app/core/admin/lti-configuration/lti-configuration.service';
import { SortService } from 'app/shared/service/sort.service';
import { LtiConfigurationComponent } from 'app/core/admin/lti-configuration/lti-configuration.component';
import { LtiPlatformConfiguration } from 'app/lti/shared/entities/lti-configuration.model';
import { AlertService } from 'app/shared/service/alert.service';

describe('LtiConfigurationComponent', () => {
    setupTestBed({ zoneless: true });

    let component: LtiConfigurationComponent;
    let fixture: ComponentFixture<LtiConfigurationComponent>;
    let mockRouter: any;
    let mockActivatedRoute: any;
    let mockSortService: any;
    let mockLtiConfigurationService: any;
    let mockAlertService: AlertService;

    beforeEach(async () => {
        mockRouter = { navigate: vi.fn() };
        mockSortService = { sortByProperty: vi.fn() };
        mockActivatedRoute = {
            data: of({ defaultSort: 'id,desc' }),
            queryParamMap: of(
                new Map([
                    ['page', '1'],
                    ['sort', 'id,asc'],
                ]),
            ),
        };
        mockLtiConfigurationService = {
            query: vi.fn().mockReturnValue(
                of(
                    new HttpResponse({
                        body: [{ id: 1, registrationId: 'platform-1' }],
                        headers: new HttpHeaders({ 'X-Total-Count': '1' }),
                    }),
                ),
            ),
            deleteLtiPlatform: vi.fn().mockReturnValue(of({})),
        };
        await TestBed.configureTestingModule({
            imports: [LtiConfigurationComponent],
            providers: [
                { provide: Router, useValue: mockRouter },
                { provide: SortService, useValue: mockSortService },
                { provide: ActivatedRoute, useValue: mockActivatedRoute },
                { provide: LtiConfigurationService, useValue: mockLtiConfigurationService },
                { provide: AlertService, useValue: { error: vi.fn() } },
            ],
        })
            .overrideTemplate(LtiConfigurationComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(LtiConfigurationComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        component.predicate.set('id');
        mockAlertService = TestBed.inject(AlertService);
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should initialize and load LTI platforms', () => {
        expect(component.platforms()).toBeDefined();
        expect(component.page()).toBe(1);
        expect(component.predicate()).toBe('id');
        expect(component.ascending()).toBe(true);
        expect(mockLtiConfigurationService.query).toHaveBeenCalled();
        expect(component.platforms()).toHaveLength(1);
    });

    it('should generate URLs correctly', () => {
        // Test for getDynamicRegistrationUrl
        expect(component.getDynamicRegistrationUrl()).toContain('/lti/dynamic-registration');

        // Test for getDeepLinkingUrl
        expect(component.getDeepLinkingUrl()).toContain('/api/lti/public/lti13/deep-link');

        // Test for getToolUrl
        expect(component.getToolUrl()).toContain('/courses');

        // Test for getKeysetUrl
        expect(component.getKeysetUrl()).toContain('/.well-known/jwks.json');

        // Test for getInitiateLoginUrl
        expect(component.getInitiateLoginUrl()).toContain('/api/lti/public/lti13/initiate-login');

        // Test for getRedirectUri
        expect(component.getRedirectUri()).toContain('/api/lti/public/lti13/auth-callback');
    });

    it('should sort platforms', () => {
        const dummyPlatforms: LtiPlatformConfiguration[] = [
            {
                id: 1,
                customName: 'Platform A',
                clientId: 'platform-a',
                authorizationUri: 'platformA.com/auth-login',
                jwkSetUri: 'platformA.com/jwk',
                tokenUri: 'platformA.com/token',
            },
            {
                id: 2,
                customName: 'Platform B',
                clientId: 'platform-b',
                authorizationUri: 'platformB.com/auth-login',
                jwkSetUri: 'platformB.com/jwk',
                tokenUri: 'platformB.com/token',
            },
        ];
        component.platforms.set(dummyPlatforms);
        component.sortRows();
        expect(mockSortService.sortByProperty).toHaveBeenCalledWith(dummyPlatforms, 'id', false);
    });

    it('should delete an LTI platform and navigate', () => {
        const platformIdToDelete = 1;
        component.deleteLtiPlatform(platformIdToDelete);
        expect(mockLtiConfigurationService.deleteLtiPlatform).toHaveBeenCalledWith(platformIdToDelete);
        expect(component.platforms()).toHaveLength(0);
    });

    it('should handle navigation on transition', () => {
        component.transition();
        expect(mockRouter.navigate).toHaveBeenCalledWith(['/admin/lti-configuration'], {
            queryParams: {
                page: component.page(),
                sort: 'id,asc',
            },
        });
    });

    it('should handle errors on deleting LTI platform', () => {
        const errorResponse = new HttpErrorResponse({ status: 500, statusText: 'Server Error', error: { message: 'Error occurred' } });
        const errorSpy = vi.spyOn(mockAlertService, 'error');
        mockLtiConfigurationService.deleteLtiPlatform.mockReturnValue(throwError(() => errorResponse));
        component.deleteLtiPlatform(123);
        expect(errorSpy).toHaveBeenCalled();
    });
});
