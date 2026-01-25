/**
 * Vitest tests for AdminPasskeyDTO interface.
 * Since this is a TypeScript interface/DTO, these tests verify
 * the structure and type compliance of objects conforming to the interface.
 */
import { describe, expect, it } from 'vitest';
import type { AdminPasskeyDTO } from './admin-passkey.dto';

describe('AdminPasskeyDTO', () => {
    it('should accept a valid AdminPasskeyDTO object', () => {
        const validDTO: AdminPasskeyDTO = {
            credentialId: 'base64url-encoded-credential-id',
            label: 'My Security Key',
            created: '2024-01-01T00:00:00Z',
            lastUsed: '2024-01-15T10:30:00Z',
            isSuperAdminApproved: false,
            userId: 12345,
            userLogin: 'john.doe',
            userName: 'John Doe',
        };

        expect(validDTO.credentialId).toBe('base64url-encoded-credential-id');
        expect(validDTO.label).toBe('My Security Key');
        expect(validDTO.created).toBe('2024-01-01T00:00:00Z');
        expect(validDTO.lastUsed).toBe('2024-01-15T10:30:00Z');
        expect(validDTO.isSuperAdminApproved).toBe(false);
        expect(validDTO.userId).toBe(12345);
        expect(validDTO.userLogin).toBe('john.doe');
        expect(validDTO.userName).toBe('John Doe');
    });

    it('should accept an AdminPasskeyDTO with super admin approval', () => {
        const approvedDTO: AdminPasskeyDTO = {
            credentialId: 'admin-credential-id',
            label: 'Admin YubiKey',
            created: '2023-06-15T08:00:00Z',
            lastUsed: '2024-01-20T14:45:00Z',
            isSuperAdminApproved: true,
            userId: 1,
            userLogin: 'admin',
            userName: 'System Administrator',
        };

        expect(approvedDTO.isSuperAdminApproved).toBe(true);
        expect(approvedDTO.userId).toBe(1);
    });

    it('should have all required fields defined', () => {
        const dto: AdminPasskeyDTO = {
            credentialId: 'cred-123',
            label: 'Test Key',
            created: '2024-01-01T00:00:00Z',
            lastUsed: '2024-01-01T00:00:00Z',
            isSuperAdminApproved: false,
            userId: 100,
            userLogin: 'testuser',
            userName: 'Test User',
        };

        // Verify all required properties are present
        expect(dto).toHaveProperty('credentialId');
        expect(dto).toHaveProperty('label');
        expect(dto).toHaveProperty('created');
        expect(dto).toHaveProperty('lastUsed');
        expect(dto).toHaveProperty('isSuperAdminApproved');
        expect(dto).toHaveProperty('userId');
        expect(dto).toHaveProperty('userLogin');
        expect(dto).toHaveProperty('userName');
    });

    it('should handle ISO 8601 date strings correctly', () => {
        const dto: AdminPasskeyDTO = {
            credentialId: 'date-test-credential',
            label: 'Date Test Key',
            created: '2024-03-15T09:30:45.123Z',
            lastUsed: '2024-03-20T15:45:30.000+02:00',
            isSuperAdminApproved: false,
            userId: 200,
            userLogin: 'dateuser',
            userName: 'Date User',
        };

        // Verify dates can be parsed
        const createdDate = new Date(dto.created);
        const lastUsedDate = new Date(dto.lastUsed);

        expect(createdDate).toBeInstanceOf(Date);
        expect(lastUsedDate).toBeInstanceOf(Date);
        expect(createdDate.getTime()).not.toBeNaN();
        expect(lastUsedDate.getTime()).not.toBeNaN();
    });

    it('should work with various credential ID formats', () => {
        const dtos: AdminPasskeyDTO[] = [
            {
                credentialId: 'simple-id',
                label: 'Simple ID',
                created: '2024-01-01T00:00:00Z',
                lastUsed: '2024-01-01T00:00:00Z',
                isSuperAdminApproved: false,
                userId: 1,
                userLogin: 'user1',
                userName: 'User One',
            },
            {
                credentialId: 'AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_',
                label: 'Base64url ID',
                created: '2024-01-01T00:00:00Z',
                lastUsed: '2024-01-01T00:00:00Z',
                isSuperAdminApproved: false,
                userId: 2,
                userLogin: 'user2',
                userName: 'User Two',
            },
            {
                credentialId: '',
                label: 'Empty Credential ID',
                created: '2024-01-01T00:00:00Z',
                lastUsed: '2024-01-01T00:00:00Z',
                isSuperAdminApproved: false,
                userId: 3,
                userLogin: 'user3',
                userName: 'User Three',
            },
        ];

        expect(dtos).toHaveLength(3);
        expect(dtos[0].credentialId).toBe('simple-id');
        expect(dtos[1].credentialId).toBe('AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_');
        expect(dtos[2].credentialId).toBe('');
    });

    it('should handle special characters in user name', () => {
        const dto: AdminPasskeyDTO = {
            credentialId: 'special-chars-credential',
            label: 'Key with special label: @#$%',
            created: '2024-01-01T00:00:00Z',
            lastUsed: '2024-01-01T00:00:00Z',
            isSuperAdminApproved: false,
            userId: 400,
            userLogin: 'user.name@domain.com',
            userName: "O'Brien, Jean-Paul",
        };

        expect(dto.userName).toBe("O'Brien, Jean-Paul");
        expect(dto.userLogin).toBe('user.name@domain.com');
        expect(dto.label).toContain('@#$%');
    });

    it('should be usable in arrays for list operations', () => {
        const passkeyList: AdminPasskeyDTO[] = [
            {
                credentialId: 'cred-1',
                label: 'Key 1',
                created: '2024-01-01T00:00:00Z',
                lastUsed: '2024-01-01T00:00:00Z',
                isSuperAdminApproved: true,
                userId: 1,
                userLogin: 'admin',
                userName: 'Admin User',
            },
            {
                credentialId: 'cred-2',
                label: 'Key 2',
                created: '2024-01-02T00:00:00Z',
                lastUsed: '2024-01-02T00:00:00Z',
                isSuperAdminApproved: false,
                userId: 2,
                userLogin: 'user',
                userName: 'Regular User',
            },
        ];

        // Test filtering by super admin approval
        const approvedKeys = passkeyList.filter((key) => key.isSuperAdminApproved);
        expect(approvedKeys).toHaveLength(1);
        expect(approvedKeys[0].userLogin).toBe('admin');

        // Test finding by user ID
        const userKey = passkeyList.find((key) => key.userId === 2);
        expect(userKey).toBeDefined();
        expect(userKey?.label).toBe('Key 2');

        // Test mapping to labels
        const labels = passkeyList.map((key) => key.label);
        expect(labels).toEqual(['Key 1', 'Key 2']);
    });

    it('should support object spread and assignment', () => {
        const originalDTO: AdminPasskeyDTO = {
            credentialId: 'original-cred',
            label: 'Original Key',
            created: '2024-01-01T00:00:00Z',
            lastUsed: '2024-01-01T00:00:00Z',
            isSuperAdminApproved: false,
            userId: 500,
            userLogin: 'original',
            userName: 'Original User',
        };

        // Test spread operator for copying
        const copiedDTO: AdminPasskeyDTO = { ...originalDTO };
        expect(copiedDTO).toEqual(originalDTO);
        expect(copiedDTO).not.toBe(originalDTO);

        // Test spread with override
        const updatedDTO: AdminPasskeyDTO = {
            ...originalDTO,
            isSuperAdminApproved: true,
            lastUsed: '2024-06-01T00:00:00Z',
        };

        expect(updatedDTO.isSuperAdminApproved).toBe(true);
        expect(updatedDTO.lastUsed).toBe('2024-06-01T00:00:00Z');
        expect(updatedDTO.credentialId).toBe('original-cred');
    });
});
