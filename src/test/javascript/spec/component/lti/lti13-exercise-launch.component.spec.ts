import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { Lti13ExerciseLaunchComponent } from 'app/lti/lti13-exercise-launch.component';
import { ActivatedRoute, ActivatedRouteSnapshot, convertToParamMap } from '@angular/router';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { HttpClient } from '@angular/common/http';
import { of, throwError } from 'rxjs';

describe('Lti13ExerciseLaunchComponent', () => {
    let fixture: ComponentFixture<Lti13ExerciseLaunchComponent>;
    let comp: Lti13ExerciseLaunchComponent;
    let route: ActivatedRoute;
    let http: HttpClient;

    beforeEach(() => {
        route = {
            snapshot: { queryParamMap: convertToParamMap({ state: 'state', id_token: 'id_token' }) },
        } as ActivatedRoute;

        window.sessionStorage.setItem('state', 'state');

        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule],
            providers: [{ provide: ActivatedRoute, useValue: route }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(Lti13ExerciseLaunchComponent);
                comp = fixture.componentInstance;
            });

        http = TestBed.inject(HttpClient);
    });

    afterEach(() => {
        window.sessionStorage.clear();
        jest.restoreAllMocks();
    });

    it('onInit fail without state', () => {
        const httpStub = jest.spyOn(http, 'post');
        const consoleSpy = jest.spyOn(console, 'error').mockImplementation();

        route.snapshot = { queryParamMap: convertToParamMap({ id_token: 'id_token' }) } as ActivatedRouteSnapshot;

        comp.ngOnInit();

        expect(consoleSpy).toHaveBeenCalledOnce();
        expect(consoleSpy).toHaveBeenCalledWith('Required parameter for LTI launch missing');
        expect(comp.isLaunching).toBeFalse();
        expect(httpStub).not.toHaveBeenCalled();
    });

    it('onInit fail without token', () => {
        const httpStub = jest.spyOn(http, 'post');
        const consoleSpy = jest.spyOn(console, 'error').mockImplementation();

        route.snapshot = { queryParamMap: convertToParamMap({ state: 'state' }) } as ActivatedRouteSnapshot;

        comp.ngOnInit();

        expect(consoleSpy).toHaveBeenCalledOnce();
        expect(consoleSpy).toHaveBeenCalledWith('Required parameter for LTI launch missing');
        expect(comp.isLaunching).toBeFalse();
        expect(httpStub).not.toHaveBeenCalled();
    });

    it('onInit state does not match', () => {
        const httpStub = jest.spyOn(http, 'post');
        const consoleSpy = jest.spyOn(console, 'error').mockImplementation();

        window.sessionStorage.setItem('state', 'notMatch');

        comp.ngOnInit();

        expect(consoleSpy).toHaveBeenCalledOnce();
        expect(consoleSpy).toHaveBeenCalledWith('LTI launch state mismatch');
        expect(comp.isLaunching).toBeFalse();
        expect(httpStub).not.toHaveBeenCalled();
    });

    it('onInit no targetLinkUri', () => {
        const httpStub = jest.spyOn(http, 'post').mockReturnValue(of({}));
        const consoleSpy = jest.spyOn(console, 'error').mockImplementation();

        expect(comp.isLaunching).toBeTrue();

        comp.ngOnInit();

        expect(consoleSpy).toHaveBeenCalledOnce();
        expect(consoleSpy).toHaveBeenCalledWith('No LTI targetLinkUri received for a successful launch');
        expect(httpStub).toHaveBeenCalledOnce();
        expect(httpStub).toHaveBeenCalledWith('api/public/lti13/auth-login', expect.anything(), expect.anything());

        expect(comp.isLaunching).toBeFalse();
    });

    it('onInit success to call launch endpoint', () => {
        const targetLink = window.location.host + '/targetLink';
        const httpStub = jest.spyOn(http, 'post').mockReturnValue(of({ targetLinkUri: targetLink }));

        expect(comp.isLaunching).toBeTrue();

        comp.ngOnInit();

        expect(httpStub).toHaveBeenCalledOnce();
        expect(httpStub).toHaveBeenCalledWith('api/public/lti13/auth-login', expect.anything(), expect.anything());
    });

    it('onInit launch fails on error', () => {
        const httpStub = jest.spyOn(http, 'post').mockReturnValue(
            throwError(() => ({
                status: 400,
                error: {},
            })),
        );

        comp.ngOnInit();

        expect(httpStub).toHaveBeenCalledOnce();
        expect(httpStub).toHaveBeenCalledWith('api/public/lti13/auth-login', expect.anything(), expect.anything());

        expect(comp.isLaunching).toBeFalse();
    });
});
