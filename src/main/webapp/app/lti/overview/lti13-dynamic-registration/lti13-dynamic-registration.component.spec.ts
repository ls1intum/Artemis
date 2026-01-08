import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Lti13DynamicRegistrationComponent } from 'app/lti/overview/lti13-dynamic-registration/lti13-dynamic-registration.component';
import { ActivatedRoute, ActivatedRouteSnapshot, Params, Router, convertToParamMap } from '@angular/router';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpClient, provideHttpClient } from '@angular/common/http';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { of, throwError } from 'rxjs';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('Lti13DynamicRegistrationComponentTest', () => {
    setupTestBed({ zoneless: true });
    let fixture: ComponentFixture<Lti13DynamicRegistrationComponent>;
    let comp: Lti13DynamicRegistrationComponent;
    let route: ActivatedRoute;
    let http: HttpClient;

    beforeEach(async () => {
        route = {
            params: of({ courseId: 1 }) as Params,
            snapshot: { queryParamMap: convertToParamMap({ openid_configuration: 'config', registration_token: 'token' }) },
        } as ActivatedRoute;

        await TestBed.configureTestingModule({
            imports: [Lti13DynamicRegistrationComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useClass: MockRouter },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(Lti13DynamicRegistrationComponent);
        comp = fixture.componentInstance;

        http = TestBed.inject(HttpClient);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('onInit without query params to fail', () => {
        route.snapshot = { queryParamMap: convertToParamMap({}) } as ActivatedRouteSnapshot;

        comp.ngOnInit();

        expect(comp.isRegistering).toBe(false);
        expect(comp.registeredSuccessfully).toBe(false);
    });

    it('onInit success to call dynamic registration endpoint', () => {
        const httpStub = vi.spyOn(http, 'post').mockReturnValue(of({ body: {} }));

        expect(comp.isRegistering).toBe(true);

        comp.ngOnInit();

        expect(httpStub).toHaveBeenCalledOnce();
        expect(httpStub).toHaveBeenCalledWith('api/lti/admin/lti13/dynamic-registration', null, expect.anything());

        expect(comp.isRegistering).toBe(false);
        expect(comp.registeredSuccessfully).toBe(true);
    });

    it('onInit dynamic registration fails on error', () => {
        const httpStub = vi.spyOn(http, 'post').mockReturnValue(
            throwError(() => ({
                status: 400,
                error: {},
            })),
        );

        comp.ngOnInit();

        expect(httpStub).toHaveBeenCalledOnce();
        expect(httpStub).toHaveBeenCalledWith('api/lti/admin/lti13/dynamic-registration', null, expect.anything());

        expect(comp.isRegistering).toBe(false);
        expect(comp.registeredSuccessfully).toBe(false);
    });

    it('should extract courseId from params', () => {
        vi.spyOn(http, 'post').mockReturnValue(of({ body: {} }));

        comp.ngOnInit();

        expect(comp.courseId).toBe(1);
    });

    it('should send registration request without registration_token if not provided', () => {
        route.snapshot = { queryParamMap: convertToParamMap({ openid_configuration: 'config' }) } as ActivatedRouteSnapshot;
        const httpStub = vi.spyOn(http, 'post').mockReturnValue(of({ body: {} }));

        comp.ngOnInit();

        expect(httpStub).toHaveBeenCalledOnce();
        expect(comp.registeredSuccessfully).toBe(true);
    });

    it('should post message to parent window after registration completes', () => {
        const mockPostMessage = vi.fn();
        const originalOpener = window.opener;
        window.opener = { postMessage: mockPostMessage };

        vi.spyOn(http, 'post').mockReturnValue(of({ body: {} }));

        comp.ngOnInit();

        expect(mockPostMessage).toHaveBeenCalledWith({ subject: 'org.imsglobal.lti.close' }, '*');

        window.opener = originalOpener;
    });

    it('should post message to parent window after registration fails', () => {
        const mockPostMessage = vi.fn();
        const originalParent = window.parent;
        Object.defineProperty(window, 'opener', { value: null, writable: true });
        Object.defineProperty(window, 'parent', { value: { postMessage: mockPostMessage }, writable: true });

        vi.spyOn(http, 'post').mockReturnValue(
            throwError(() => ({
                status: 400,
                error: {},
            })),
        );

        comp.ngOnInit();

        expect(mockPostMessage).toHaveBeenCalledWith({ subject: 'org.imsglobal.lti.close' }, '*');

        Object.defineProperty(window, 'parent', { value: originalParent, writable: true });
    });
});
