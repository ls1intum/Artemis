import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { User } from 'app/account/user/user.model';
import { CourseForRoleAssignment, UserCourseRole } from 'app/account/user/shared/user-course-role.model';
import { SKIP_HTTP_ERROR_ALERT } from 'app/core/interceptor/errorhandler.interceptor';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AdminUserService } from 'app/account/user/shared/admin-user.service';
import { provideHttpClient } from '@angular/common/http';
import { AuthorityFilter, OriginFilter, RegistrationNumberFilter, StatusFilter, UserFilter } from 'app/admin/user-management/user-management.component';

describe('AdminUserService', () => {
    let adminService: AdminUserService;
    let httpMock: HttpTestingController;
    const resourceUrl = '/api/account/admin/users';
    const paging = { page: 0, pageSize: 10, sortedColumn: 'login', sortingOrder: 'ASCENDING' };

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
            adminService.findUser('first.last@example.org').subscribe();

            const req = httpMock.expectOne({ method: 'GET' });
            expect(req.request.url).toBe(`${resourceUrl}/first.last%40example.org`);
            req.flush({ login: 'first.last@example.org' });
        });

        it('should return User', () => {
            adminService.findUser('user').subscribe((received) => {
                expect(received.login).toBe('user');
                expect(received.createdDate).toEqual(new Date('2026-01-01T10:00:00Z'));
            });

            const req = httpMock.expectOne({ method: 'GET' });
            req.flush({ id: 1, login: 'user', createdDate: '2026-01-01T10:00:00Z' });
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

    describe('getCourseRoles', () => {
        it('should request the course roles of the user and return them', () => {
            const courseRoles: UserCourseRole[] = [{ courseId: 1, courseTitle: 'Algorithms', courseShortName: 'ALGO', role: 'STUDENT' }];
            let received: UserCourseRole[] | undefined;

            adminService.getCourseRoles('first.last@example.org').subscribe((roles) => (received = roles));

            const req = httpMock.expectOne({ method: 'GET' });
            expect(req.request.url).toBe(`${resourceUrl}/first.last%40example.org/course-roles`);
            expect(req.request.context.get(SKIP_HTTP_ERROR_ALERT)).toBe(true);
            req.flush(courseRoles);
            expect(received).toEqual(courseRoles);
        });
    });

    describe('course role editing', () => {
        it('should search the courses by the given term and size', () => {
            let received: CourseForRoleAssignment[] | undefined;

            adminService.searchCoursesForRoleAssignment('algo', 5).subscribe((courses) => (received = courses));

            const req = httpMock.expectOne((request) => request.url === 'api/admin/courses/for-role-assignment');
            expect(req.request.method).toBe('GET');
            expect(req.request.params.get('searchTerm')).toBe('algo');
            expect(req.request.params.get('size')).toBe('5');
            req.flush([{ id: 1, title: 'Algorithms' }]);
            expect(received).toEqual([{ id: 1, title: 'Algorithms' }]);
        });

        it.each([
            ['INSTRUCTOR', 'instructors'],
            ['EDITOR', 'editors'],
            ['TEACHING_ASSISTANT', 'tutors'],
            ['STUDENT', 'students'],
        ] as const)('should add the %s role through the %s membership endpoint', (role, slug) => {
            adminService.addCourseRole('user', 7, role).subscribe();

            const req = httpMock.expectOne({ method: 'POST', url: `api/course/courses/7/${slug}/user` });
            req.flush(null);
        });

        it.each([
            ['INSTRUCTOR', 'instructors'],
            ['EDITOR', 'editors'],
            ['TEACHING_ASSISTANT', 'tutors'],
            ['STUDENT', 'students'],
        ] as const)('should remove the %s role through the %s membership endpoint', (role, slug) => {
            adminService.removeCourseRole('user', 7, role).subscribe();

            const req = httpMock.expectOne({ method: 'DELETE', url: `api/course/courses/7/${slug}/user` });
            req.flush(null);
        });
    });

    describe('create', () => {
        it('should create a new user', () => {
            const user = new User(undefined, 'newuser', 'First', 'Last', 'email@test.com');

            adminService.create(user).subscribe((response) => {
                expect(response.body?.login).toBe('newuser');
                expect(response.status).toBe(201);
                expect(response.headers.get('Location')).toBe(`${resourceUrl}/newuser`);
            });

            const req = httpMock.expectOne({ method: 'POST', url: resourceUrl });
            expect(JSON.parse(req.request.serializeBody() as string)).toEqual({ login: 'newuser', firstName: 'First', lastName: 'Last', email: 'email@test.com' });
            req.flush({ id: 1, login: 'newuser' }, { status: 201, statusText: 'Created', headers: { Location: `${resourceUrl}/newuser` } });
        });
    });

    describe('update', () => {
        it('should update a user', () => {
            const user = new User(1, 'existinguser', 'Updated', 'Name');
            user.isTestUser = false;
            user.organizations = [{ id: 3 }];
            user.revokeCredentials = true;
            user.password = 'new-password';

            adminService.update(user).subscribe((response) => {
                expect(response.body?.firstName).toBe('Updated');
                expect(response.headers.get('X-artemisApp-alert')).toBe('artemisApp.userManagement.updated');
            });

            const req = httpMock.expectOne({ method: 'PUT', url: resourceUrl });
            expect(JSON.parse(req.request.serializeBody() as string)).toEqual({
                id: 1,
                login: 'existinguser',
                firstName: 'Updated',
                lastName: 'Name',
                organizations: [{ id: 3 }],
                isTestUser: false,
                revokeCredentials: true,
                password: 'new-password',
            });
            req.flush({ id: 1, login: 'existinguser', firstName: 'Updated' }, { headers: { 'X-artemisApp-alert': 'artemisApp.userManagement.updated' } });
        });
    });

    describe('importAll', () => {
        it('should import users from ldap', () => {
            const users: Partial<User>[] = [{ login: 'user1' }, { visibleRegistrationNumber: '12345', isTestUser: false }];

            adminService.importAll(users).subscribe((response) => {
                expect(response.body).toEqual([expect.objectContaining({ visibleRegistrationNumber: '12345', isTestUser: false })]);
            });

            const req = httpMock.expectOne({ method: 'POST', url: `${resourceUrl}/import` });
            expect(JSON.parse(req.request.serializeBody() as string)).toEqual([{ login: 'user1' }, { registrationNumber: '12345', isTestUser: false }]);
            req.flush([{ registrationNumber: '12345', isTestUser: false }]);
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
            adminService.query({ ...paging, searchTerm: 'a+b', findWithoutCourseEnrollment: false }).subscribe((response) => {
                expect(response.body?.length).toBe(2);
                expect(response.headers.get('X-Total-Count')).toBe('21');
                expect(response.headers.get('Link')).toContain('next');
            });

            const req = httpMock.expectOne((r) => new URL(r.urlWithParams, 'https://artemis.example').pathname === resourceUrl && r.method === 'GET');
            const query = new URL(req.request.urlWithParams, 'https://artemis.example').searchParams;
            expect(query.get('page')).toBe('0');
            expect(query.get('pageSize')).toBe('10');
            expect(query.get('sortedColumn')).toBe('login');
            expect(query.get('sortingOrder')).toBe('ASCENDING');
            expect(query.get('searchTerm')).toBe('a+b');
            expect(query.get('findWithoutCourseEnrollment')).toBe('false');
            req.flush([{ login: 'user1' }, { login: 'user2' }], { headers: { 'X-Total-Count': '21', Link: '</api/account/admin/users?page=1>; rel="next"' } });
        });

        it('should query users with filter', () => {
            const filter = new UserFilter();
            filter.authorityFilter.add(AuthorityFilter.ADMIN);
            filter.authorityFilter.add(AuthorityFilter.USER);
            filter.originFilter.add(OriginFilter.INTERNAL);
            filter.statusFilter.add(StatusFilter.ACTIVATED);
            filter.registrationNumberFilter.add(RegistrationNumberFilter.WITH_REG_NO);

            adminService.query(paging, filter).subscribe();

            const req = httpMock.expectOne((r) => new URL(r.urlWithParams, 'https://artemis.example').pathname === resourceUrl && r.method === 'GET');
            const query = new URL(req.request.urlWithParams, 'https://artemis.example').searchParams;
            expect(query.getAll('authorities')).toEqual(['ADMIN,USER']);
            expect(query.get('origins')).toBe('INTERNAL');
            expect(query.get('status')).toBe('ACTIVATED');
            expect(query.get('registrationNumbers')).toBe('WITH_REG_NO');
            req.flush([]);
        });

        it('should query users with noAuthority filter', () => {
            const filter = new UserFilter();
            filter.noAuthority = true;

            adminService.query(paging, filter).subscribe(() => {});

            const req = httpMock.expectOne((r) => new URL(r.urlWithParams, 'https://artemis.example').pathname === resourceUrl && r.method === 'GET');
            const query = new URL(req.request.urlWithParams, 'https://artemis.example').searchParams;
            expect(query.get('authorities')).toBe('NO_AUTHORITY');
            expect(query.get('origins')).toBe('');
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
            expect(req.request.body).toBeNull();
            req.flush({ id: userId, login: 'synceduser' });
        });

        it('should propagate the LDAP-disabled error', () => {
            const error = vi.fn();
            adminService.syncLdap(123).subscribe({ error });
            const problem = { errorKey: 'ldapNotEnabled' };
            httpMock.expectOne({ method: 'PUT', url: `${resourceUrl}/123/sync-ldap` }).flush(problem, { status: 400, statusText: 'Bad Request' });
            expect(error).toHaveBeenCalledWith(expect.objectContaining({ status: 400, error: problem }));
        });
    });

    describe('deleteUser', () => {
        it('should delete a user by login', () => {
            const login = 'first.last@example.org';

            adminService.deleteUser(login, 'current-impact').subscribe((response) => {
                expect(response.status).toBe(200);
                expect(response.body).toEqual({ login, status: 'DELETED' });
            });

            const req = httpMock.expectOne({ method: 'DELETE', url: `${resourceUrl}/first.last%40example.org` });
            expect(req.request.body).toEqual({ impactFingerprint: 'current-impact' });
            expect(req.request.detectContentTypeHeader()).toBe('application/json');
            req.flush({ login, status: 'DELETED' });
        });

        it.each([409, 403])('should propagate a %s deletion result', (status) => {
            const result = { login: 'user', status: status === 409 ? 'PLAN_CHANGED' : 'FORBIDDEN' };
            const error = vi.fn();
            adminService.deleteUser('user', 'old-impact').subscribe({ error });
            httpMock.expectOne({ method: 'DELETE', url: `${resourceUrl}/user` }).flush(result, { status, statusText: result.status });
            expect(error).toHaveBeenCalledWith(expect.objectContaining({ status, error: result }));
        });
    });

    describe('deleteUsers', () => {
        it('should delete multiple users by logins', () => {
            const deletionRequest = {
                users: [
                    { login: 'user1', impactFingerprint: 'impact-1' },
                    { login: 'user2', impactFingerprint: 'impact-2' },
                ],
            };

            const result = [
                { login: 'user1', status: 'DELETED' },
                { login: 'user2', status: 'PLAN_CHANGED' },
            ];
            adminService.deleteUsers(deletionRequest).subscribe((response) => {
                expect(response.status).toBe(200);
                expect(response.body).toEqual(result);
            });

            const req = httpMock.expectOne({ method: 'DELETE', url: resourceUrl });
            expect(req.request.body).toEqual(deletionRequest);
            expect(req.request.detectContentTypeHeader()).toBe('application/json');
            req.flush(result);
        });
    });

    describe('deletion impact', () => {
        it('should load the impact for one user', () => {
            adminService.getDeletionImpact('user1').subscribe();

            const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/user1/deletion-impact` });
            req.flush({ login: 'user1', impactFingerprint: 'impact-1', categories: [] });
        });

        it('should load a combined impact for multiple users', () => {
            adminService.getBulkDeletionImpact(['user1', 'user2']).subscribe();

            const req = httpMock.expectOne({ method: 'POST', url: `${resourceUrl}/deletion-impact` });
            expect(req.request.body).toEqual({ logins: ['user1', 'user2'] });
            req.flush({ users: [], categories: [], totalAffectedObjects: 0 });
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
