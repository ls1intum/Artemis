import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Lti13DynamicRegistrationComponent } from 'app/lti/overview/lti13-dynamic-registration.component';
import { ActivatedRoute, ActivatedRouteSnapshot, Params, Router, convertToParamMap } from '@angular/router';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpClient, provideHttpClient } from '@angular/common/http';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { of, throwError } from 'rxjs';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('Lti13DynamicRegistrationComponentTest', () => {
    let fixture: ComponentFixture<Lti13DynamicRegistrationComponent>;
    let comp: Lti13DynamicRegistrationComponent;
    let route: ActivatedRoute;
    let http: HttpClient;

    beforeEach(() => {
        route = {
            params: of({ courseId: 1 }) as Params,
            snapshot: { queryParamMap: convertToParamMap({ openid_configuration: 'config', registration_token: 'token' }) },
        } as ActivatedRoute;

        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useClass: MockRouter },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(Lti13DynamicRegistrationComponent);
                comp = fixture.componentInstance;
            });

        http = TestBed.inject(HttpClient);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('onInit without query params to fail', () => {
        route.snapshot = { queryParamMap: convertToParamMap({}) } as ActivatedRouteSnapshot;

        comp.ngOnInit();

        expect(comp.isRegistering).toBeFalse();
        expect(comp.registeredSuccessfully).toBeFalse();
    });

    it('onInit success to call dynamic registration endpoint', () => {
        const httpStub = jest.spyOn(http, 'post').mockReturnValue(of({ body: {} }));

        expect(comp.isRegistering).toBeTrue();

        comp.ngOnInit();

        expect(httpStub).toHaveBeenCalledOnce();
        expect(httpStub).toHaveBeenCalledWith('api/lti/admin/lti13/dynamic-registration', null, expect.anything());

        expect(comp.isRegistering).toBeFalse();
        expect(comp.registeredSuccessfully).toBeTrue();
    });

    it('onInit dynamic registration fails on error', () => {
        const httpStub = jest.spyOn(http, 'post').mockReturnValue(
            throwError(() => ({
                status: 400,
                error: {},
            })),
        );

        comp.ngOnInit();

        expect(httpStub).toHaveBeenCalledOnce();
        expect(httpStub).toHaveBeenCalledWith('api/lti/admin/lti13/dynamic-registration', null, expect.anything());

        expect(comp.isRegistering).toBeFalse();
        expect(comp.registeredSuccessfully).toBeFalse();
    });
});
