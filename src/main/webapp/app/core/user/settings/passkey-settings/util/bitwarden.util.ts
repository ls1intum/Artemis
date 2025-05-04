import cloneDeep from 'lodash';
import { MalformedBitwardenCredential } from 'app/core/user/settings/passkey-settings/entities/malformed-bitwarden-credential';

/**
 * Converts a record containing numeric values into a Base64-encoded string.
 *
 * @param rawObject - which can be a record of numbers, null, or undefined.
 * @returns A Base64-encoded string representation of the object, or undefined if the input is invalid.
 */
function convertToBase64(rawObject: Record<string, number> | null | undefined): string | undefined {
    if (!rawObject || typeof rawObject !== 'object') {
        return undefined;
    }
    const uint8Array = new Uint8Array(Object.values(rawObject));
    // eslint-disable-next-line no-undef
    return btoa(String.fromCharCode(...uint8Array))
        .replace(/\//g, '_')
        .replace(/\+/g, '-');
}

/**
 * <p><strong>The Bitwarden Plugin does not work properly on Chrome; the returned credential is malformed and cannot be stringified.</strong></p>
 *
 * <p>Unfortunately, Bitwarden does not seem to show any interest in fixing this issue:
 * <a href="https://github.com/bitwarden/clients/issues/12060" target="_blank">https://github.com/bitwarden/clients/issues/12060</a>,
 * and relies on other developers to fix it within their applications. 😞</p>
 *
 * <p>As Bitwarden is a widely used password manager, we decided to implement a workaround in our application
 * to ensure a smooth user experience.</p>
 *
 * <p>The underlying issue is that Bitwarden's returned credential does not implement <code>toJSON()</code>,
 * which is required for <code>JSON.stringify()</code> to work. As a result, the browser cannot serialize the object properly,
 * causing credential registration and login with passkeys to fail when using the Bitwarden browser plugin in Chrome.</p>
 *
 * <p><strong>We fix the issue by creating a serializable copy of the object with its properties converted into the correct format.</strong></p>
 */
export function getCredentialFromInvalidBitwardenObject(malformedBitwardenCredential: MalformedBitwardenCredential | null): Credential | null {
    if (!malformedBitwardenCredential) {
        return null;
    }

    const clonedCredential: Credential = cloneDeep(malformedBitwardenCredential) as unknown as Credential;
    const credential = JSON.parse(JSON.stringify(clonedCredential));

    return {
        //@ts-expect-error authenticatorAttachment is a method in the (getAuthenticatorAttachment) object, but we return it as property here for simplicity, assuming that we only want to stringify it afterward anyway
        authenticatorAttachment: credential.authenticatorAttachment,
        clientExtensionResults: malformedBitwardenCredential.getClientExtensionResults(),
        id: credential.id,
        rawId: convertToBase64(credential.rawId),
        response: {
            attestationObject: convertToBase64(credential.response.attestationObject),
            authenticatorData: convertToBase64(
                malformedBitwardenCredential.response.authenticatorData ?? malformedBitwardenCredential.response.getAuthenticatorData?.() ?? undefined,
            ),
            clientDataJSON: convertToBase64(credential.response.clientDataJSON),
            publicKey: convertToBase64(malformedBitwardenCredential.response.getPublicKey?.() ?? undefined),
            publicKeyAlgorithm: malformedBitwardenCredential.response.getPublicKeyAlgorithm?.() ?? undefined,
            transports: malformedBitwardenCredential.response.getTransports?.() ?? undefined,
            signature: convertToBase64(malformedBitwardenCredential.response.signature),
            userHandle: convertToBase64(malformedBitwardenCredential.response.userHandle),
        },
        type: credential.type,
    };
}
