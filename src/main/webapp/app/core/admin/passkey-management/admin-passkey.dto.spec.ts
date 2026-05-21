import { describe, expect, it } from 'vitest';
import type { AdminPasskeyDTO } from './admin-passkey.dto';

describe('AdminPasskeyDTO', () => {
    it('should create a valid AdminPasskeyDTO object with all required fields', () => {
        const dto: AdminPasskeyDTO = {
            credentialId: 'base64url-encoded-credential-id',
            label: 'My Security Key',
            created: '2024-01-01T00:00:00Z',
            lastUsed: '2024-01-15T10:30:00Z',
            isSuperAdminApproved: false,
            userId: 12345,
            userLogin: 'john.doe',
            userName: 'John Doe',
        };

        expect(dto.credentialId).toBe('base64url-encoded-credential-id');
        expect(dto.label).toBe('My Security Key');
        expect(dto.isSuperAdminApproved).toBe(false);
        expect(dto.userId).toBe(12345);
        expect(dto.userLogin).toBe('john.doe');
        expect(dto.userName).toBe('John Doe');
    });
});
