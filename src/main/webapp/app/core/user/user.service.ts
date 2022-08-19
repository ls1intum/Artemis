import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { User } from 'app/core/user/user.model';

@Injectable({ providedIn: 'root' })
export class UserService {
    public resourceUrl = SERVER_API_URL + 'api/users';

    constructor(private http: HttpClient) {}

    /**
     * Find a user on the server.
     * @param login The login of the user to find.
     * @return Observable<HttpResponse<User>> with the found user as body.
     */
    find(login: string): Observable<User> {
        return this.http.get<User>(`${this.resourceUrl}/${login}`);
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
     * Initializes an LTI user and returns the newly generated password.
     */
    initializeLTIUser(): Observable<HttpResponse<{ password: string }>> {
        return this.http.put<{ password: string }>(`${this.resourceUrl}/initialize`, null, { observe: 'response' });
    }
}
