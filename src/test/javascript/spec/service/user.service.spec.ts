import { TestBed } from '@angular/core/testing';
import { User } from 'app/core/user/user.model';
import { UserService } from 'app/core/user/user.service';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { Authority } from 'app/shared/constants/authority.constants';
import { AdminUserService } from 'app/core/user/admin-user.service';

describe('User Service', () => {
    let service: UserService;
    let adminService: AdminUserService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        });

        service = TestBed.inject(UserService);
        adminService = TestBed.inject(AdminUserService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('Service methods', () => {
        it('should call correct URL', () => {
            service.find('user').subscribe(() => {});

            const req = httpMock.expectOne({ method: 'GET' });
            const resourceUrl = 'api/users';
            expect(req.request.url).toBe(`${resourceUrl}/user`);
        });
        it('should return User', () => {
            service.find('user').subscribe((received) => {
                expect(received!.login).toBe('user');
            });

            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(new User(1, 'user'));
        });

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

        it('should call correct URL to accept Iris', () => {
            service.acceptIris().subscribe();
            const req = httpMock.expectOne({ method: 'PUT' });
            const resourceUrl = 'api/users/accept-iris';
            expect(req.request.url).toBe(`${resourceUrl}`);
        });

        it('should call correct URL to get Iris accepted timestamp', () => {
            service.getIrisAcceptedAt().subscribe();
            const req = httpMock.expectOne({ method: 'GET' });
            const resourceUrl = 'api/users/accept-iris';
            expect(req.request.url).toBe(`${resourceUrl}`);
        });

        it('should propagate not found response', () => {
            service.find('user').subscribe({
                error: (_error: any) => {
                    expect(_error.status).toBe(404);
                },
            });

            const req = httpMock.expectOne({ method: 'GET' });
            req.flush('Invalid request parameters', {
                status: 404,
                statusText: 'Bad Request',
            });
        });
    });
});
