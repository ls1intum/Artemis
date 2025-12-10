import { createCredentialOptions } from './credential-option.util';
import { decodeBase64url } from 'app/shared/util/base64.util';
import { User } from 'app/core/user/user.model';

jest.mock('app/shared/util/base64.util', () => ({
    decodeBase64url: jest.fn(),
}));

describe('Credential Option Util', () => {
    const mockUser: User = {
        id: '12345',
        email: 'test@example.com',
    } as unknown as User;

    const mockOptions: PublicKeyCredentialCreationOptions = {
        challenge: 'mockChallenge',
        user: undefined,
        excludeCredentials: [{ id: 'mockCredentialId', type: 'public-key' }],
        authenticatorSelection: { requireResidentKey: true, userVerification: 'preferred' },
    } as unknown as PublicKeyCredentialCreationOptions;

    beforeEach(() => {
        (decodeBase64url as jest.Mock).mockImplementation((input) => `decoded-${input}`);
    });

    it('should create valid credential options', () => {
        const result = createCredentialOptions(mockOptions, mockUser);

        expect(result).toBeDefined();
        expect(result.challenge).toBe('decoded-mockChallenge');
        expect(result.user).toEqual({
            id: new TextEncoder().encode('12345'),
            name: 'test@example.com',
            displayName: 'test@example.com',
        });
        expect(result.excludeCredentials).toEqual([{ id: 'decoded-mockCredentialId', type: 'public-key' }]);
        expect(result.authenticatorSelection).toEqual({
            requireResidentKey: true,
            userVerification: 'preferred',
        });
    });

    it('should throw an error if user ID or email is missing', () => {
        const invalidUser = { id: '', email: '' } as unknown as User;

        expect(() => createCredentialOptions(mockOptions, invalidUser)).toThrow('Invalid credential');
    });

    it('should handle missing excludeCredentials gracefully', () => {
        const optionsWithoutExcludeCredentials = Object.assign({}, mockOptions, { excludeCredentials: undefined });

        const result = createCredentialOptions(optionsWithoutExcludeCredentials, mockUser);

        expect(result.excludeCredentials).toBeUndefined();
    });
});
