import { Injectable, OnInit } from '@angular/core';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { Observable, Subscription, tap } from 'rxjs';
import { OptionCore } from 'app/shared/user-settings/user-settings.component';
import { SERVER_API_URL } from 'app/app.constants';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { createRequestOption } from 'app/shared/util/request-util';

@Injectable({ providedIn: 'root' })
export class UserSettingsService {
    public resourceUrl = SERVER_API_URL + 'api/user-settings';

    constructor(private accountService: AccountService, private http: HttpClient) {}

    queryUserOptions(req?: any): Observable<HttpResponse<OptionCore[]>> {
        const optionCores = createRequestOption(req);
        return this.http.get<OptionCore[]>(this.resourceUrl + '/fetch-options', { params: optionCores, observe: 'response' });
    }

    saveUserOptions(optionCores: OptionCore[]): Observable<HttpResponse<OptionCore[]>> {
        return this.http.post<OptionCore[]>(this.resourceUrl + '/save-new-options', optionCores, { observe: 'response' });
    }
}
