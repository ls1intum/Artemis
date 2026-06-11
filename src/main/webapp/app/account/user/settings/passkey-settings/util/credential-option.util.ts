import { decodeBase64url } from 'app/foundation/util/base64.util';
import { User } from 'app/account/user/user.model';

export function createCredentialOptions(options: PublicKeyCredentialCreationOptions, user: User): PublicKeyCredentialCreationOptions {
    const username = user.email;

    if (!user.id || !username) {
        throw new Error('Invalid credential');
    }

    return {
        ...options,
        challenge: decodeBase64url(options.challenge),
        user: {
            id: new TextEncoder().encode(user.id.toString()),
            name: username,
            displayName: username,
        },
        excludeCredentials: options.excludeCredentials?.map((credential) => ({
            ...credential,
            id: decodeBase64url(credential.id),
        })),
    };
}
