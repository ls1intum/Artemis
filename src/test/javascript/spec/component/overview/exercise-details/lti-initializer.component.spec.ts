import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LtiInitializerComponent } from 'app/overview/exercise-details/lti-initializer.component';
import { UserService } from 'app/core/user/user.service';
import { MockUserService } from '../../../helpers/mocks/service/mock-user.service';
import { MockActivatedRoute } from '../../../helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { AlertService } from 'app/core/util/alert.service';
import { MockProvider } from 'ng-mocks';

describe('LtiInitializerComponent', () => {
    let comp: LtiInitializerComponent;
    let fixture: ComponentFixture<LtiInitializerComponent>;
    let userService: UserService;
    let activatedRoute: ActivatedRoute;
    let alertService: AlertService;
    let router: Router;

    let initializeLTIUserStub: jest.SpyInstance;
    let infoSpy: jest.SpyInstance;
    let navigateSpy: jest.SpyInstance;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            declarations: [LtiInitializerComponent],
            providers: [
                MockProvider(AlertService),
                { provide: UserService, useClass: MockUserService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: Router, useClass: MockRouter },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(LtiInitializerComponent);
                comp = fixture.componentInstance;
                userService = fixture.debugElement.injector.get(UserService);
                activatedRoute = fixture.debugElement.injector.get(ActivatedRoute);
                alertService = fixture.debugElement.injector.get(AlertService);
                router = fixture.debugElement.injector.get(Router);

                initializeLTIUserStub = jest.spyOn(userService, 'initializeLTIUser');
                infoSpy = jest.spyOn(alertService, 'info');
                navigateSpy = jest.spyOn(router, 'navigate');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should not initialize without flag', () => {
        comp.ngOnInit();
        expect(initializeLTIUserStub).not.toHaveBeenCalled();
        expect(infoSpy).not.toHaveBeenCalled();
        expect(navigateSpy).not.toHaveBeenCalled();
        expect(comp.modalRef).toBe(undefined);
    });

    it('should initialize and display with flag', () => {
        const returnedPassword = 'ThisIsARandomPassword';
        activatedRoute.queryParams = of({ initialize: '' });
        initializeLTIUserStub.mockReturnValue(of({ body: { password: returnedPassword } }));

        comp.ngOnInit();
        expect(initializeLTIUserStub).toHaveBeenCalledOnce();
        expect(infoSpy).not.toHaveBeenCalled();
        expect(navigateSpy).not.toHaveBeenCalled();
        expect(comp.modalRef).not.toBe(undefined); // External reference
    });

    it('should end initialization without password', () => {
        (activatedRoute as MockActivatedRoute).setParameters({ initialize: '' });
        initializeLTIUserStub.mockReturnValue(of({ body: { password: undefined } }));

        comp.ngOnInit();
        expect(initializeLTIUserStub).toHaveBeenCalledOnce();
        expect(infoSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(comp.modalRef).toBe(undefined);
    });
});
