import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class SshUserSettingsFingerprintsService {
    private http = inject(HttpClient);

    error?: string;

    public async getSshFingerprints(): Promise<{ [key: string]: string }> {
        return await firstValueFrom(this.http.get<{ [key: string]: string }>('api/localvc/ssh-fingerprints'));
    }
}
