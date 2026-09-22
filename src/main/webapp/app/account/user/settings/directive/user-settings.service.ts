import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { User } from 'app/account/user/user.model';

@Injectable({ providedIn: 'root' })
export class UserSettingsService {
    private http = inject(HttpClient);

    public profilePictureResourceUrl = 'api/account/profile-picture';

    public updateProfilePicture(file: Blob) {
        const formData = new FormData();
        formData.append('file', file, 'placeholderName.jpeg');

        return this.http.put<User>(`${this.profilePictureResourceUrl}`, formData, { observe: 'response' });
    }

    public removeProfilePicture() {
        return this.http.delete<User>(`${this.profilePictureResourceUrl}`, { observe: 'response' });
    }
}
