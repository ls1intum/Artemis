import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { createRequestOption } from 'app/shared/util/request.util';
import { User } from 'app/core/user/user.model';
import { UserFilter } from 'app/core/admin/user-management/user-management.component';

@Injectable({ providedIn: 'root' })
export class AdminUserService {
    private http = inject(HttpClient);

    public resourceUrl = 'api/core/admin/users';

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
    query(req?: any, filter?: UserFilter): Observable<HttpResponse<User[]>> {
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
    deleteUser(login: string): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/${login}`, { observe: 'response' });
    }

    /**
     * Delete users on the server.
     * @param logins The logins of the users to delete.
     * @return Observable<HttpResponse<void>>
     */
    deleteUsers(logins: string[]): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}`, { body: logins, observe: 'response' });
    }

    /**
     * Get the authorities.
     */
    authorities(): Observable<string[]> {
        return this.http.get<string[]>(`${this.resourceUrl}/authorities`);
    }
}
