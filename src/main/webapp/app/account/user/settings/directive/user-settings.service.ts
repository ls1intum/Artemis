import { Service, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AccountApi } from 'app/openapi/api/account-api';
import { User as ApiUser } from 'app/openapi/model/user';

@Service()
export class UserSettingsService {
    private readonly accountApi = inject(AccountApi);

    public updateProfilePicture(file: Blob): Observable<ApiUser> {
        return this.accountApi.updateProfilePicture(new File([file], 'placeholderName.jpeg', { type: file.type }));
    }

    public removeProfilePicture(): Observable<void> {
        return this.accountApi.removeProfilePicture();
    }
}
