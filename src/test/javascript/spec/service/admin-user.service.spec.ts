import { TestBed } from '@angular/core/testing';
import { User } from 'app/core/user/user.model';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { AdminUserService } from 'app/core/user/admin-user.service';

describe('User Service', () => {
    let adminService: AdminUserService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        });

        adminService = TestBed.inject(AdminUserService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('Service methods', () => {
        it('should call correct URL', () => {
            adminService.findUser('user').subscribe(() => {});

            const req = httpMock.expectOne({ method: 'GET' });
            const resourceUrl = 'api/admin/users';
            expect(req.request.url).toBe(`${resourceUrl}/user`);
        });
        it('should return User', () => {
            adminService.findUser('user').subscribe((received) => {
                expect(received!.login).toBe('user');
            });

            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(new User(1, 'user'));
        });

        it('should propagate not found response', () => {
            adminService.findUser('user').subscribe({
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
