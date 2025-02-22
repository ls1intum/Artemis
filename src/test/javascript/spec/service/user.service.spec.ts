import { TestBed } from '@angular/core/testing';
import { UserService } from 'app/core/user/user.service';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Authority } from 'app/shared/constants/authority.constants';
import { AdminUserService } from 'app/core/user/admin-user.service';
import { provideHttpClient } from '@angular/common/http';

describe('User Service', () => {
    let service: UserService;
    let adminService: AdminUserService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });

        service = TestBed.inject(UserService);
        adminService = TestBed.inject(AdminUserService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('Service methods', () => {
        it('should return Authorities', () => {
            adminService.authorities().subscribe((_authorities) => {
                expect(_authorities).toEqual([Authority.USER, Authority.ADMIN]);
            });
            const req = httpMock.expectOne({ method: 'GET' });

            req.flush([Authority.USER, Authority.ADMIN]);
        });

        it('should call correct URL to update lastNotificationRead', () => {
            service.updateLastNotificationRead().subscribe();
            const req = httpMock.expectOne({ method: 'PUT' });
            const resourceUrl = 'api/users/notification-date';
            expect(req.request.url).toBe(`${resourceUrl}`);
        });

        it('should call correct URL to update notification visibility', () => {
            service.updateNotificationVisibility(true).subscribe();
            const req = httpMock.expectOne({ method: 'PUT' });
            const resourceUrl = 'api/users/notification-visibility';
            expect(req.request.url).toBe(`${resourceUrl}`);
        });

        it('should call correct URL to initialize LTI user', () => {
            service.initializeLTIUser().subscribe();
            const req = httpMock.expectOne({ method: 'PUT' });
            const resourceUrl = 'api/users/initialize';
            expect(req.request.url).toBe(`${resourceUrl}`);
        });

        it('should call correct URL to accept external LLM', () => {
            service.acceptExternalLLMUsage().subscribe();
            const req = httpMock.expectOne({ method: 'PUT' });
            const resourceUrl = 'api/users/accept-external-llm-usage';
            expect(req.request.url).toBe(`${resourceUrl}`);
        });
    });
});
