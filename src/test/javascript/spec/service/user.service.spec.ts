import { TestBed } from '@angular/core/testing';
import { User } from 'app/core/user/user.model';
import { UserService } from 'app/core/user/user.service';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { Authority } from 'app/shared/constants/authority.constants';

describe('User Service', () => {
    let service: UserService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        });

        service = TestBed.inject(UserService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('Service methods', () => {
        it('should call correct URL', () => {
            service.find('user').subscribe(() => {});

            const req = httpMock.expectOne({ method: 'GET' });
            const resourceUrl = SERVER_API_URL + 'api/users';
            expect(req.request.url).toEqual(`${resourceUrl}/user`);
        });
        it('should return User', () => {
            service.find('user').subscribe((received) => {
                expect(received!.login).toEqual('user');
            });

            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(new User(1, 'user'));
        });

        it('should return Authorities', () => {
            service.authorities().subscribe((_authorities) => {
                expect(_authorities).toEqual([Authority.USER, Authority.ADMIN]);
            });
            const req = httpMock.expectOne({ method: 'GET' });

            req.flush([Authority.USER, Authority.ADMIN]);
        });

        it('should call correct URL to update lastNotificationRead', () => {
            service.updateLastNotificationRead().subscribe();
            const req = httpMock.expectOne({ method: 'PUT' });
            const resourceUrl = SERVER_API_URL + 'api/users/notification-date';
            expect(req.request.url).toEqual(`${resourceUrl}`);
        });

        it('should call correct URL to update notification visibility', () => {
            service.updateNotificationVisibility(true).subscribe();
            const req = httpMock.expectOne({ method: 'PUT' });
            const resourceUrl = SERVER_API_URL + 'api/users/notification-visibility';
            expect(req.request.url).toEqual(`${resourceUrl}`);
        });

        it('should propagate not found response', () => {
            service.find('user').subscribe(null, (_error: any) => {
                expect(_error.status).toEqual(404);
            });

            const req = httpMock.expectOne({ method: 'GET' });
            req.flush('Invalid request parameters', {
                status: 404,
                statusText: 'Bad Request',
            });
        });
    });
});
