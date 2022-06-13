import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

type JwtToken = {
    id_token: string;
};

@Injectable({ providedIn: 'root' })
export class PersonalAccessTokenService {
    private tokenUrl = SERVER_API_URL + 'api/personal-access-token';

    constructor(private http: HttpClient) {}

    getTokenMaxLifetimeMillis(): Observable<number> {
        return this.http.get<number>(this.tokenUrl);
    }

    generateNewToken(lifetimeMillis: number): Observable<string> {
        return this.http.post<JwtToken>(this.tokenUrl, lifetimeMillis).pipe(map((resp: JwtToken) => resp['id_token']));
    }
}
