import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class SshUserSettingsFingerprintsService {
    error?: string;

    private http = inject(HttpClient);

    public async getSshFingerprints(): Promise<{ [key: string]: string }> {
        return await firstValueFrom(this.http.get<{ [key: string]: string }>('api/programming/ssh-fingerprints'));
    }
}
