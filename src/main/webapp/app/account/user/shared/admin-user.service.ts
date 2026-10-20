import { Service, inject } from '@angular/core';
import { HttpClient, HttpContext, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SKIP_HTTP_ERROR_ALERT } from 'app/core/interceptor/errorhandler.interceptor';
import { createRequestOption } from 'app/foundation/util/request.util';
import { User } from 'app/account/user/user.model';
import { COURSE_ROLE_SLUGS, CourseForRoleAssignment, CourseRoleName, UserCourseRole } from 'app/account/user/shared/user-course-role.model';
import { UserFilter } from 'app/admin/user-management/user-management.component';
import { BulkUserDeletionImpact, BulkUserDeletionRequest, UserDeletionImpact, UserDeletionResult } from 'app/account/user/shared/user-deletion.model';

@Service()
export class AdminUserService {
    private http = inject(HttpClient);

    public resourceUrl = 'api/account/admin/users';

    private readonly courseResourceUrl = 'api/course/courses';

    /**
     * Create a user on the server.
     * @param user The user to create.
     * @return Observable<HttpResponse<User>> with the created user as body.
     */
    create(user: User): Observable<HttpResponse<User>> {
        return this.http.post<User>(this.resourceUrl, user, { observe: 'response' });
    }

    /**
     * Import a list of users from ldap to artemis
     * @param users The list of users to be imported.
     * @return Observable<HttpResponse<User[]>> with not found Users
     */
    importAll(users: Partial<User>[]): Observable<HttpResponse<User[]>> {
        return this.http.post<User[]>(`${this.resourceUrl}/import`, users, { observe: 'response' });
    }

    /**
     * Update a user on the server: this should only be used when groups and authorities are included, otherwise they might be lost on the server
     * @param user The user to update.
     * @return Observable<HttpResponse<User>> with the updated user as body.
     */
    update(user: User): Observable<HttpResponse<User>> {
        return this.http.put<User>(this.resourceUrl, user, { observe: 'response' });
    }

    /**
     * Activate a user on the server (by an admin)
     * @param userId The id of the user to activate.
     * @return Observable<HttpResponse<User>> with the updated user as body.
     */
    activate(userId: number): Observable<HttpResponse<User>> {
        return this.http.patch<User>(`${this.resourceUrl}/${userId}/activate`, null, { observe: 'response' });
    }

    /**
     * Deactivate a user on the server (by an admin)
     * @param userId The id of the user to deactivate.
     * @return Observable<HttpResponse<User>> with the updated user as body.
     */
    deactivate(userId: number): Observable<HttpResponse<User>> {
        return this.http.patch<User>(`${this.resourceUrl}/${userId}/deactivate`, null, { observe: 'response' });
    }

    /**
     * Submit a query for a given request.
     * @param req The query request
     * @param filter additional filter
     * @return Observable<HttpResponse<User[]>> with the list of users that match the query as body.
     */
    query(req?: Record<string, unknown>, filter?: UserFilter): Observable<HttpResponse<User[]>> {
        let options = createRequestOption(req);
        if (filter) {
            options = filter.adjustOptions(options);
        }
        return this.http.get<User[]>(this.resourceUrl, { params: options, observe: 'response' });
    }

    /**
     * Submit a query for all logins of not enrolled users (no admins)
     * @return Observable<HttpResponse<string[]>> with the sorted list of all logins of not enrolled users
     */
    queryNotEnrolledUsers(): Observable<HttpResponse<string[]>> {
        return this.http.get<string[]>(`${this.resourceUrl}/not-enrolled`, { observe: 'response' });
    }

    /**
     * Find a user on the server.
     * @param login The login of the user to find.
     * @return Observable<HttpResponse<User>> with the found user as body.
     */
    findUser(login: string): Observable<User> {
        return this.http.get<User>(`${this.resourceUrl}/${login}`);
    }

    /**
     * Get the courses in which a user holds a role, with one entry per course and role.
     * The caller shows a failure next to the course roles, so no separate error alert is raised.
     * @param login The login of the user.
     * @return Observable<UserCourseRole[]> with the course roles of the user, ordered by course title.
     */
    getCourseRoles(login: string): Observable<UserCourseRole[]> {
        return this.http.get<UserCourseRole[]>(`${this.resourceUrl}/${login}/course-roles`, { context: new HttpContext().set(SKIP_HTTP_ERROR_ALERT, true) });
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
        return this.http.put<User>(`${this.resourceUrl}/${userId}/sync-ldap`, { observe: 'response' });
    }

    /**
     * Delete a user on the server.
     * @param login The login of the user to delete.
     * @return Observable<HttpResponse<void>>
     */
    deleteUser(login: string, impactFingerprint = ''): Observable<HttpResponse<UserDeletionResult>> {
        return this.http.delete<UserDeletionResult>(`${this.resourceUrl}/${login}`, { body: { impactFingerprint }, observe: 'response' });
    }

    /**
     * Preview the exact impact of permanently deleting one user.
     */
    getDeletionImpact(login: string): Observable<HttpResponse<UserDeletionImpact>> {
        return this.http.get<UserDeletionImpact>(`${this.resourceUrl}/${login}/deletion-impact`, { observe: 'response' });
    }

    /**
     * Preview and aggregate the exact impact of permanently deleting multiple users.
     */
    getBulkDeletionImpact(logins: string[]): Observable<HttpResponse<BulkUserDeletionImpact>> {
        return this.http.post<BulkUserDeletionImpact>(`${this.resourceUrl}/deletion-impact`, { logins }, { observe: 'response' });
    }

    /**
     * Delete users after confirming their individual impact fingerprints.
     */
    deleteUsers(request: BulkUserDeletionRequest): Observable<HttpResponse<UserDeletionResult[]>> {
        return this.http.delete<UserDeletionResult[]>(`${this.resourceUrl}`, { body: request, observe: 'response' });
    }

    /**
     * Get the authorities.
     */
    authorities(): Observable<string[]> {
        return this.http.get<string[]>(`${this.resourceUrl}/authorities`);
    }
}
