import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { createRequestOption } from 'app/shared/util/request.util';
import { User } from 'app/core/user/user.model';
import { UserFilter } from 'app/admin/user-management/user-management.component';

@Injectable({ providedIn: 'root' })
export class UserService {
    public resourceUrl = SERVER_API_URL + 'api/users';

    constructor(private http: HttpClient) {}

    /**
     * Create a user on the server.
     * @param user The user to create.
     * @return Observable<HttpResponse<User>> with the created user as body.
     */
    create(user: User): Observable<HttpResponse<User>> {
        return this.http.post<User>(this.resourceUrl, user, { observe: 'response' });
    }

    /**
     * Update a user on the server.
     * @param user The user to update.
     * @return Observable<HttpResponse<User>> with the updated user as body.
     */
    update(user: User): Observable<HttpResponse<User>> {
        return this.http.put<User>(this.resourceUrl, user, { observe: 'response' });
    }

    /**
     * Call the LDAP server to update the info of a user on the server.
     * @param user The user to be updated from the LDAP server.
     * @return Observable<User> with the updated user as body.
     */
    syncLdap(login: string): Observable<User> {
        return this.http.put<User>(`${this.resourceUrl}/${login}/ldap-sync`, { observe: 'response' });
    }

    /**
     * Find a user on the server.
     * @param login The login of the user to find.
     * @return Observable<HttpResponse<User>> with the found user as body.
     */
    find(login: string): Observable<User> {
        return this.http.get<User>(`${this.resourceUrl}/${login}`);
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
     * Search for a user on the server by login or name.
     * @param loginOrName The login or name to search for.
     * @return Observable<HttpResponse<User[]>> with the list of found users as body.
     */
    search(loginOrName: string): Observable<HttpResponse<User[]>> {
        return this.http.get<User[]>(`${this.resourceUrl}/search?loginOrName=${loginOrName}`, { observe: 'response' });
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
        let params = new HttpParams();
        for (const login of logins) {
            params = params.append('login', login);
        }
        return this.http.delete<void>(`${this.resourceUrl}`, { params, observe: 'response' });
    }

    /**
     * Update the user notification date.
     */
    updateLastNotificationRead(): Observable<HttpResponse<void>> {
        return this.http.put<void>(`${this.resourceUrl}/notification-date`, null, { observe: 'response' });
    }

    /**
     * Updates the property that decides what notifications should be displayed or hidden in the notification sidebar based on notification date.
     * If the value is set to null -> show all notifications
     * (Not to be confused with the notification settings. This filter is only based on the date a notification was created)
     */
    updateNotificationVisibility(showAllNotifications: boolean): Observable<HttpResponse<void>> {
        return this.http.put<void>(`${this.resourceUrl}/notification-visibility`, showAllNotifications, { observe: 'response' });
    }

    /**
     * Get the authorities.
     */
    authorities(): Observable<string[]> {
        return this.http.get<string[]>(`${this.resourceUrl}/authorities`);
    }

    /**
     * Initializes an LTI user and returns the newly generated password.
     */
    initializeLTIUser(): Observable<HttpResponse<{ password: string }>> {
        return this.http.put<{ password: string }>(`${this.resourceUrl}/initialize`, null, { observe: 'response' });
    }
}
