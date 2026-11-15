import { Service, inject } from '@angular/core';
import { HttpClient, HttpContext, HttpResponse } from '@angular/common/http';
import { Observable, map, throwError } from 'rxjs';
import dayjs from 'dayjs/esm';

import { SKIP_HTTP_ERROR_ALERT } from 'app/core/interceptor/errorhandler.interceptor';
import { createRequestOption } from 'app/foundation/util/request.util';
import { User } from 'app/account/user/user.model';
import { COURSE_ROLE_SLUGS, CourseForRoleAssignment, CourseRoleName, UserCourseRole } from 'app/account/user/shared/user-course-role.model';
import { UserFilter } from 'app/admin/user-management/user-management.component';
import { BulkUserDeletionImpact, BulkUserDeletionRequest, UserDeletionImpact, UserDeletionResult } from 'app/account/user/shared/user-deletion.model';

import { AdminUserApi } from 'app/openapi/api/admin-user-api';
import { ManagedUserVM } from 'app/openapi/model/managed-user-vm';
import { Student } from 'app/openapi/model/student';
import { User as ApiUser } from 'app/openapi/model/user';
import { LLMSelectionDecision } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';
import { SortingOrder } from 'app/foundation/pagination/pageable-table';
import { hydrate } from 'app/foundation/util/deep-clone.util';

@Service()
export class AdminUserService {
    private readonly adminUserApi = inject(AdminUserApi);
    private http = inject(HttpClient);

    public resourceUrl = 'api/account/admin/users';

    private readonly courseResourceUrl = 'api/course/courses';

    /**
     * Create a user on the server.
     * @param user The user to create.
     * @return Observable<HttpResponse<User>> with the created user as body.
     */
    create(user: User): Observable<HttpResponse<User>> {
        const request = this.managedUserRequest(user);
        return request
            ? this.adminUserApi.createUser(request).pipe(map((response) => this.mapUserResponse(response)))
            : throwError(() => new Error('Creating a user requires a login.'));
    }

    /**
     * Import a list of users from ldap to artemis
     * @param users The list of users to be imported.
     * @return Observable<HttpResponse<User[]>> with not found Users
     */
    importAll(users: Partial<User>[]): Observable<HttpResponse<User[]>> {
        const students: Student[] = users.map((user) => ({
            login: user.login,
            firstName: user.firstName,
            lastName: user.lastName,
            email: user.email,
            registrationNumber: 'registrationNumber' in user && typeof user.registrationNumber === 'string' ? user.registrationNumber : user.visibleRegistrationNumber,
            isTestUser: user.isTestUser,
        }));
        return this.adminUserApi.importUsers(students).pipe(
            map((response) =>
                response.clone<User[]>({
                    body: response.body?.map(({ registrationNumber, ...student }) => hydrate(new User(), student, { visibleRegistrationNumber: registrationNumber })) ?? null,
                }),
            ),
        );
    }

    /**
     * Update a user on the server: this should only be used when groups and authorities are included, otherwise they might be lost on the server
     * @param user The user to update.
     * @return Observable<HttpResponse<User>> with the updated user as body.
     */
    update(user: User): Observable<HttpResponse<User>> {
        const request = this.managedUserRequest(user);
        return request
            ? this.adminUserApi.updateUser(request).pipe(map((response) => this.mapUserResponse(response)))
            : throwError(() => new Error('Updating a user requires a login.'));
    }

    /**
     * Activate a user on the server (by an admin)
     * @param userId The id of the user to activate.
     * @return Observable<HttpResponse<User>> with the updated user as body.
     */
    activate(userId: number): Observable<HttpResponse<User>> {
        return this.adminUserApi.activateUser(userId).pipe(map((response) => this.mapUserResponse(response)));
    }

    /**
     * Deactivate a user on the server (by an admin)
     * @param userId The id of the user to deactivate.
     * @return Observable<HttpResponse<User>> with the updated user as body.
     */
    deactivate(userId: number): Observable<HttpResponse<User>> {
        return this.adminUserApi.deactivateUser(userId).pipe(map((response) => this.mapUserResponse(response)));
    }

    /**
     * Submit a query for a given request.
     * @param req The query request
     * @param filter additional filter
     * @return Observable<HttpResponse<User[]>> with the list of users that match the query as body.
     */
    query(req?: Record<string, unknown>, filter?: UserFilter): Observable<HttpResponse<User[]>> {
        const request = createRequestOption(req);
        const options = filter?.adjustOptions(request) ?? request;
        const csvValues = (name: string) => options.getAll(name)?.flatMap((value) => value.split(','));
        const numberValue = (name: string) => (options.has(name) ? Number(options.get(name)) : undefined);
        const pageSize = numberValue('pageSize');
        const sortedColumn = options.get('sortedColumn');
        if (pageSize === undefined || sortedColumn === null) {
            return throwError(() => new Error('Listing users requires a page size and sort column.'));
        }
        const sortingOrder = options.get('sortingOrder');
        const withoutEnrollment = options.get('findWithoutCourseEnrollment');
        return this.adminUserApi
            .getAllUsers(
                pageSize,
                sortedColumn,
                csvValues('authorities'),
                csvValues('origins'),
                csvValues('status'),
                withoutEnrollment === null ? undefined : withoutEnrollment === 'true',
                csvValues('registrationNumbers'),
                options.get('searchTerm') ?? undefined,
                numberValue('page'),
                sortingOrder === SortingOrder.ASCENDING || sortingOrder === SortingOrder.DESCENDING ? sortingOrder : undefined,
            )
            .pipe(map((response) => response.clone<User[]>({ body: response.body?.map((user) => this.mapUser(user)) ?? null })));
    }

    /**
     * Submit a query for all logins of not enrolled users (no admins)
     * @return Observable<HttpResponse<string[]>> with the sorted list of all logins of not enrolled users
     */
    queryNotEnrolledUsers(): Observable<HttpResponse<string[]>> {
        return this.adminUserApi.getNotEnrolledUsers();
    }

    /**
     * Find a user on the server.
     * @param login The login of the user to find.
     * @return Observable<HttpResponse<User>> with the found user as body.
     */
    findUser(login: string): Observable<User> {
        return this.adminUserApi.getUser(login).pipe(map((user) => this.mapUser(user)));
    }

    /**
     * Get the courses in which a user holds a role, with one entry per course and role.
     * The caller shows a failure next to the course roles, so no separate error alert is raised.
     * @param login The login of the user.
     * @return Observable<UserCourseRole[]> with the course roles of the user, ordered by course title.
     */
    getCourseRoles(login: string): Observable<UserCourseRole[]> {
        return this.adminUserApi.getCourseRoles(login, { context: new HttpContext().set(SKIP_HTTP_ERROR_ALERT, true) });
    }

    /**
     * Search the courses in which a course role can be assigned, by a part of their title or short name.
     * @param searchTerm The text to look for in the title and the short name of the courses.
     * @param size The maximum number of courses to return.
     * @return Observable<CourseForRoleAssignment[]> with the best matching courses, ordered by title.
     */
    searchCoursesForRoleAssignment(searchTerm: string, size = 10): Observable<CourseForRoleAssignment[]> {
        return this.http.get<CourseForRoleAssignment[]>('api/admin/courses/for-role-assignment', { params: { searchTerm, size } });
    }

    /**
     * Give a user a role in a course. The server also keeps the global authorities of the user in sync. Adding a role the user already has changes nothing.
     * @param login The login of the user.
     * @param courseId The id of the course.
     * @param role The role to grant.
     */
    addCourseRole(login: string, courseId: number, role: CourseRoleName): Observable<void> {
        return this.http.post<void>(`${this.courseResourceUrl}/${courseId}/${COURSE_ROLE_SLUGS[role]}/${login}`, null);
    }

    /**
     * Remove a role of a user in a course. The server also keeps the global authorities of the user in sync.
     * @param login The login of the user.
     * @param courseId The id of the course.
     * @param role The role to revoke.
     */
    removeCourseRole(login: string, courseId: number, role: CourseRoleName): Observable<void> {
        return this.http.delete<void>(`${this.courseResourceUrl}/${courseId}/${COURSE_ROLE_SLUGS[role]}/${login}`);
    }

    /**
     * Call the LDAP server to update the info of a user on the server.
     * @param userId The id of the user to be updated from the LDAP server.
     * @return Observable<User> with the updated user as body.
     */
    syncLdap(userId: number): Observable<User> {
        return this.adminUserApi.syncUserViaLdap(userId).pipe(map((response) => this.mapUserResponse(response).body!));
    }

    /**
     * Delete a user on the server.
     * @param login The login of the user to delete.
     * @return Observable<HttpResponse<void>>
     */
    deleteUser(login: string, impactFingerprint = ''): Observable<HttpResponse<UserDeletionResult>> {
        return this.adminUserApi.deleteUser(login, { impactFingerprint });
    }

    /**
     * Preview the exact impact of permanently deleting one user.
     */
    getDeletionImpact(login: string): Observable<HttpResponse<UserDeletionImpact>> {
        return this.adminUserApi.getUserDeletionImpact(login);
    }

    /**
     * Preview and aggregate the exact impact of permanently deleting multiple users.
     */
    getBulkDeletionImpact(logins: string[]): Observable<HttpResponse<BulkUserDeletionImpact>> {
        return this.adminUserApi.getBulkUserDeletionImpact({ logins });
    }

    /**
     * Delete users after confirming their individual impact fingerprints.
     */
    deleteUsers(request: BulkUserDeletionRequest): Observable<HttpResponse<UserDeletionResult[]>> {
        return this.adminUserApi.deleteUsers(request);
    }

    /**
     * Get the authorities.
     */
    authorities(): Observable<string[]> {
        return this.adminUserApi.getAuthorities();
    }

    private managedUserRequest(user: User): ManagedUserVM | undefined {
        if (user.login === undefined) {
            return undefined;
        }
        return {
            id: user.id,
            login: user.login,
            firstName: user.firstName,
            lastName: user.lastName,
            email: user.email,
            imageUrl: user.imageUrl,
            langKey: user.langKey,
            activated: user.activated,
            internal: user.internal,
            visibleRegistrationNumber: user.visibleRegistrationNumber,
            authorities: user.authorities,
            organizations: user.organizations,
            password: user.password,
            revokeCredentials: user.revokeCredentials,
            isTestUser: user.isTestUser,
        };
    }

    private mapUserResponse(response: HttpResponse<ApiUser>): HttpResponse<User> {
        return response.clone<User>({ body: response.body === null ? null : this.mapUser(response.body) });
    }

    private mapUser({ createdDate, lastModifiedDate, selectedLLMUsage, selectedLLMUsageTimestamp, ...user }: ApiUser): User {
        return hydrate(new User(), user, {
            createdDate: createdDate ? new Date(createdDate) : undefined,
            lastModifiedDate: lastModifiedDate ? new Date(lastModifiedDate) : undefined,
            selectedLLMUsage: selectedLLMUsage ? LLMSelectionDecision[selectedLLMUsage] : undefined,
            selectedLLMUsageTimestamp: selectedLLMUsageTimestamp ? dayjs(selectedLLMUsageTimestamp) : undefined,
        });
    }
}
