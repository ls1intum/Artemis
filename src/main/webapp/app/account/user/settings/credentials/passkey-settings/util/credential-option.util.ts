import { decodeBase64url } from 'app/foundation/util/base64.util';
import { User } from 'app/account/user/user.model';
import { cloneWith } from 'app/foundation/util/deep-clone.util';

export function createCredentialOptions(options: PublicKeyCredentialCreationOptions, user: User): PublicKeyCredentialCreationOptions {
    const username = user.email;

    if (!user.id || !username) {
        throw new Error('Invalid credential');
    }

    return cloneWith(options, {
        challenge: decodeBase64url(options.challenge),
        user: {
            id: new TextEncoder().encode(user.id.toString()),
            name: username,
            displayName: username,
        },
        excludeCredentials: options.excludeCredentials?.map((credential) => cloneWith(credential, { id: decodeBase64url(credential.id) })),
    });
}
