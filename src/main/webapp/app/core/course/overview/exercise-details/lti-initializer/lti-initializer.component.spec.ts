import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LtiInitializerComponent } from 'app/core/course/overview/exercise-details/lti-initializer/lti-initializer.component';
import { UserService } from 'app/core/user/shared/user.service';
import { MockUserService } from 'test/helpers/mocks/service/mock-user.service';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { AlertService } from 'app/shared/service/alert.service';
import { MockProvider } from 'ng-mocks';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';

describe('LtiInitializerComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: LtiInitializerComponent;
    let fixture: ComponentFixture<LtiInitializerComponent>;
    let userService: UserService;
    let activatedRoute: ActivatedRoute;
    let alertService: AlertService;
    let router: Router;

    let initializeLTIUserStub: ReturnType<typeof vi.spyOn>;
    let infoSpy: ReturnType<typeof vi.spyOn>;
    let navigateSpy: ReturnType<typeof vi.spyOn>;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            providers: [
                MockProvider(AlertService),
                { provide: UserService, useClass: MockUserService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({}) },
                { provide: Router, useClass: MockRouter },
                { provide: AccountService, useClass: MockAccountService },
                { provide: NgbModal, useClass: MockNgbModalService },
            ],
        });
        await TestBed.compileComponents();
        fixture = TestBed.createComponent(LtiInitializerComponent);
        comp = fixture.componentInstance;
        userService = TestBed.inject(UserService);
        activatedRoute = TestBed.inject(ActivatedRoute);
        alertService = TestBed.inject(AlertService);
        router = TestBed.inject(Router);

        initializeLTIUserStub = vi.spyOn(userService, 'initializeLTIUser');
        infoSpy = vi.spyOn(alertService, 'info');
        navigateSpy = vi.spyOn(router, 'navigate');
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should not initialize without flag', () => {
        comp.ngOnInit();
        expect(initializeLTIUserStub).not.toHaveBeenCalled();
        expect(infoSpy).not.toHaveBeenCalled();
        expect(navigateSpy).not.toHaveBeenCalled();
        expect(comp.modalRef).toBeUndefined();
    });

    it('should initialize and display with flag', () => {
        const returnedPassword = 'ThisIsARandomPassword';
        activatedRoute.queryParams = of({ initialize: '' });
        initializeLTIUserStub.mockReturnValue(of({ body: { password: returnedPassword } }));

        comp.ngOnInit();
        expect(initializeLTIUserStub).toHaveBeenCalledOnce();
        expect(infoSpy).not.toHaveBeenCalled();
        expect(navigateSpy).not.toHaveBeenCalled();
        expect(comp.modalRef).toBeDefined(); // External reference
    });

    it('should end initialization without password', () => {
        (activatedRoute as MockActivatedRoute).setParameters({ initialize: '' });
        initializeLTIUserStub.mockReturnValue(of({ body: { password: undefined } }));

        comp.ngOnInit();
        expect(initializeLTIUserStub).toHaveBeenCalledOnce();
        expect(infoSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(comp.modalRef).toBeUndefined();
    });
});
