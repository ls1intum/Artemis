import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { User } from 'app/core/user/user.model';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AdminUserService } from 'app/core/user/shared/admin-user.service';
import { provideHttpClient } from '@angular/common/http';
import { AuthorityFilter, OriginFilter, RegistrationNumberFilter, StatusFilter, UserFilter } from 'app/core/admin/user-management/user-management.component';

describe('AdminUserService', () => {
    setupTestBed({ zoneless: true });

    let adminService: AdminUserService;
    let httpMock: HttpTestingController;
    const resourceUrl = 'api/core/admin/users';

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });

        adminService = TestBed.inject(AdminUserService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    describe('findUser', () => {
        it('should call correct URL', () => {
            adminService.findUser('user').subscribe(() => {});

            const req = httpMock.expectOne({ method: 'GET' });
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

    describe('create', () => {
        it('should create a new user', () => {
            const user = new User(undefined, 'newuser', 'First', 'Last', 'email@test.com');

            adminService.create(user).subscribe((response) => {
                expect(response.body?.login).toBe('newuser');
            });

            const req = httpMock.expectOne({ method: 'POST', url: resourceUrl });
            expect(req.request.body).toEqual(user);
            req.flush(new User(1, 'newuser'));
        });
    });

    describe('update', () => {
        it('should update a user', () => {
            const user = new User(1, 'existinguser', 'Updated', 'Name');

            adminService.update(user).subscribe((response) => {
                expect(response.body?.firstName).toBe('Updated');
            });

            const req = httpMock.expectOne({ method: 'PUT', url: resourceUrl });
            expect(req.request.body).toEqual(user);
            req.flush(new User(1, 'existinguser', 'Updated', 'Name'));
        });
    });

    describe('importAll', () => {
        it('should import users from ldap', () => {
            const users: Partial<User>[] = [{ login: 'user1' }, { login: 'user2' }];

            adminService.importAll(users).subscribe((response) => {
                expect(response.body?.length).toBe(0);
            });

            const req = httpMock.expectOne({ method: 'POST', url: `${resourceUrl}/import` });
            expect(req.request.body).toEqual(users);
            req.flush([]);
        });
    });

    describe('activate', () => {
        it('should activate a user', () => {
            const userId = 123;

            adminService.activate(userId).subscribe((response) => {
                expect(response.body?.activated).toBe(true);
            });

            const req = httpMock.expectOne({ method: 'PATCH', url: `${resourceUrl}/${userId}/activate` });
            expect(req.request.body).toBeNull();
            req.flush(new User(userId, 'user', undefined, undefined, undefined, true));
        });
    });

    describe('deactivate', () => {
        it('should deactivate a user', () => {
            const userId = 123;

            adminService.deactivate(userId).subscribe((response) => {
                expect(response.body?.activated).toBe(false);
            });

            const req = httpMock.expectOne({ method: 'PATCH', url: `${resourceUrl}/${userId}/deactivate` });
            expect(req.request.body).toBeNull();
            req.flush(new User(userId, 'user', undefined, undefined, undefined, false));
        });
    });

    describe('query', () => {
        it('should query users without filter', () => {
            adminService.query({ page: 0, size: 10 }).subscribe((response) => {
                expect(response.body?.length).toBe(2);
            });

            const req = httpMock.expectOne((r) => r.url === resourceUrl && r.method === 'GET');
            expect(req.request.params.get('page')).toBe('0');
            expect(req.request.params.get('size')).toBe('10');
            req.flush([new User(1, 'user1'), new User(2, 'user2')]);
        });

        it('should query users with filter', () => {
            const filter = new UserFilter();
            filter.authorityFilter.add(AuthorityFilter.ADMIN);
            filter.originFilter.add(OriginFilter.INTERNAL);
            filter.statusFilter.add(StatusFilter.ACTIVATED);
            filter.registrationNumberFilter.add(RegistrationNumberFilter.WITH_REG_NO);

            adminService.query({ page: 0 }, filter).subscribe((response) => {
                expect(response.body?.length).toBeGreaterThanOrEqual(0);
            });

            const req = httpMock.expectOne((r) => r.url === resourceUrl && r.method === 'GET');
            expect(req.request.params.get('authorities')).toBe('ADMIN');
            expect(req.request.params.get('origins')).toBe('INTERNAL');
            expect(req.request.params.get('status')).toBe('ACTIVATED');
            expect(req.request.params.get('registrationNumbers')).toBe('WITH_REG_NO');
            req.flush([]);
        });

        it('should query users with noAuthority filter', () => {
            const filter = new UserFilter();
            filter.noAuthority = true;

            adminService.query({}, filter).subscribe(() => {});

            const req = httpMock.expectOne((r) => r.url === resourceUrl && r.method === 'GET');
            expect(req.request.params.get('authorities')).toBe('NO_AUTHORITY');
            req.flush([]);
        });
    });

    describe('queryNotEnrolledUsers', () => {
        it('should query not enrolled users', () => {
            adminService.queryNotEnrolledUsers().subscribe((response) => {
                expect(response.body?.length).toBe(2);
            });

            const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/not-enrolled` });
            req.flush(['user1', 'user2']);
        });
    });

    describe('syncLdap', () => {
        it('should sync user from LDAP', () => {
            const userId = 123;

            adminService.syncLdap(userId).subscribe((user) => {
                expect(user.id).toBe(userId);
            });

            const req = httpMock.expectOne({ method: 'PUT', url: `${resourceUrl}/${userId}/sync-ldap` });
            req.flush(new User(userId, 'synceduser'));
        });
    });

    describe('deleteUser', () => {
        it('should delete a user by login', () => {
            const login = 'userToDelete';

            adminService.deleteUser(login).subscribe((response) => {
                expect(response.status).toBe(200);
            });

            const req = httpMock.expectOne({ method: 'DELETE', url: `${resourceUrl}/${login}` });
            req.flush(null, { status: 200, statusText: 'OK' });
        });
    });

    describe('deleteUsers', () => {
        it('should delete multiple users by logins', () => {
            const logins = ['user1', 'user2', 'user3'];

            adminService.deleteUsers(logins).subscribe((response) => {
                expect(response.status).toBe(200);
            });

            const req = httpMock.expectOne({ method: 'DELETE', url: resourceUrl });
            expect(req.request.body).toEqual(logins);
            req.flush(null, { status: 200, statusText: 'OK' });
        });
    });

    describe('authorities', () => {
        it('should get all authorities', () => {
            adminService.authorities().subscribe((authorities) => {
                expect(authorities).toContain('ROLE_ADMIN');
                expect(authorities).toContain('ROLE_USER');
            });

            const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/authorities` });
            req.flush(['ROLE_ADMIN', 'ROLE_USER', 'ROLE_INSTRUCTOR']);
        });
    });
});
