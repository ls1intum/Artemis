import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AdminPasskeyDTO } from './admin-passkey.dto';
import { PasskeyDTO } from 'app/core/user/settings/passkey-settings/dto/passkey.dto';

@Injectable({ providedIn: 'root' })
export class AdminPasskeyManagementService {
    private readonly http = inject(HttpClient);
    private resourceUrl = 'api/core/passkey';

    /**
     * Get all passkeys with user information for super admin management
     */
    getAllPasskeys(): Observable<AdminPasskeyDTO[]> {
        return this.http.get<AdminPasskeyDTO[]>(`${this.resourceUrl}/admin`);
    }

    /**
     * Update the super admin approval status of a passkey
     * @param credentialId The credential ID of the passkey
     * @param isSuperAdminApproved The new approval status
     */
    updatePasskeyApproval(credentialId: string, isSuperAdminApproved: boolean): Observable<PasskeyDTO> {
        const passkeyDTO: Partial<PasskeyDTO> = { isSuperAdminApproved };
        return this.http.put<PasskeyDTO>(`${this.resourceUrl}/${credentialId}/approval`, passkeyDTO);
    }
}
