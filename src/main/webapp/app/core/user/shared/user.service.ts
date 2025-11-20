import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { User } from 'app/core/user/user.model';
import { UpdateExternalLLMUsageDto } from 'app/core/user/shared/dto/updateExternalLLMUsage.dto';

@Injectable({ providedIn: 'root' })
export class UserService {
    private http = inject(HttpClient);

    public resourceUrl = 'api/core/users';

    /**
     * Search for a user on the server by login or name.
     * @param loginOrName The login or name to search for.
     * @return Observable<HttpResponse<User[]>> with the list of found users as body.
     */
    search(loginOrName: string): Observable<HttpResponse<User[]>> {
        return this.http.get<User[]>(`${this.resourceUrl}/search?loginOrName=${loginOrName}`, { observe: 'response' });
    }

    /**
     * Initializes an LTI user and returns the newly generated password.
     */
    initializeLTIUser(): Observable<HttpResponse<{ password: string }>> {
        return this.http.put<{ password: string }>(`${this.resourceUrl}/initialize`, null, { observe: 'response' });
    }

    /**
     * Updates consent to external LLM usage policy.
     */
    updateExternalLLMUsageConsent(accepted: boolean): Observable<HttpResponse<void>> {
        const selection = accepted ? 'CLOUD_AI' : 'NO_AI';
        const updateExternalLLMUsageDto: UpdateExternalLLMUsageDto = { selection };
        return this.http.put<void>(`${this.resourceUrl}/select-llm-usage`, updateExternalLLMUsageDto, { observe: 'response' });
    }
}
