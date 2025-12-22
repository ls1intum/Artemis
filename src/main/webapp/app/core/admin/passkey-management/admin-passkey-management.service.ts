import { Injectable } from '@angular/core';
import { AdminPasskeyDTO } from './admin-passkey.dto';
import { PasskeyDTO } from 'app/core/user/settings/passkey-settings/dto/passkey.dto';
import { BaseApiHttpService } from 'app/shared/service/base-api-http.service';

@Injectable({ providedIn: 'root' })
export class AdminPasskeyManagementService extends BaseApiHttpService {
    private resourceUrl = 'core/passkey';

    /**
     * Get all passkeys with user information for super admin management
     */
    async getAllPasskeys(): Promise<AdminPasskeyDTO[]> {
        return await this.get<AdminPasskeyDTO[]>(`${this.resourceUrl}/admin`);
    }

    /**
     * Update the super admin approval status of a passkey
     * @param credentialId The credential ID of the passkey
     * @param isSuperAdminApproved The new approval status
     */
    async updatePasskeyApproval(credentialId: string, isSuperAdminApproved: boolean): Promise<PasskeyDTO> {
        return await this.put<PasskeyDTO>(`${this.resourceUrl}/${credentialId}/approval`, isSuperAdminApproved);
    }
}
