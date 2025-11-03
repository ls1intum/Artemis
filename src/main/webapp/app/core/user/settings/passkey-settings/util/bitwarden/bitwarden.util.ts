import { MalformedBitwardenRegistrationCredential } from 'app/core/user/settings/passkey-settings/entities/malformed-bitwarden-registration-credential';
import { SerializableRegistrationCredential } from 'app/core/user/settings/passkey-settings/entities/serializable-registration-credential';
import { SerializableLoginCredential } from 'app/core/user/settings/passkey-settings/entities/serializable-login-credential';
import { encodeAsBase64Url } from 'app/shared/util/base64.util';
import { MalformedBitwardenLoginCredential } from 'app/core/user/settings/passkey-settings/entities/malformed-bitwarden-login-credential';

/**
 * Converts a record containing numeric values into a Base64-encoded string.
 *
 * @param rawObject - which can be a record of numbers, null, or undefined.
 * @returns A Base64-encoded string representation of the object, or undefined if the input is invalid.
 */
export function convertToBase64(rawObject: Record<string, number> | null | undefined): string | undefined {
    if (!rawObject || typeof rawObject !== 'object') {
        return undefined;
    }
    const uint8Array = new Uint8Array(Object.values(rawObject));
    return encodeAsBase64Url(uint8Array);
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
 * causing credential registration with passkeys to fail when using the Bitwarden browser plugin in Chrome.</p>
 *
 * <p><strong>We fix the issue by creating a serializable copy of the object with its properties converted into the correct format.</strong></p>
 */
export function getRegistrationCredentialFromMalformedBitwardenObject(
    malformedBitwardenRegistrationCredential: MalformedBitwardenRegistrationCredential | null,
): SerializableRegistrationCredential | undefined {
    if (!malformedBitwardenRegistrationCredential) {
        return undefined;
    }

    return {
        authenticatorAttachment: malformedBitwardenRegistrationCredential.authenticatorAttachment as AuthenticatorAttachment,
        clientExtensionResults: malformedBitwardenRegistrationCredential.getClientExtensionResults() as AuthenticationExtensionsClientOutputs,
        id: malformedBitwardenRegistrationCredential.id,
        rawId: convertToBase64(malformedBitwardenRegistrationCredential.rawId),
        response: {
            attestationObject: convertToBase64(malformedBitwardenRegistrationCredential.response.attestationObject),
            authenticatorData: convertToBase64(malformedBitwardenRegistrationCredential.response.getAuthenticatorData?.() ?? undefined),
            clientDataJSON: convertToBase64(malformedBitwardenRegistrationCredential.response.clientDataJSON),
            publicKey: convertToBase64(malformedBitwardenRegistrationCredential.response.getPublicKey?.() ?? undefined),
            publicKeyAlgorithm: malformedBitwardenRegistrationCredential.response.getPublicKeyAlgorithm?.() ?? undefined,
            transports: malformedBitwardenRegistrationCredential.response.getTransports?.() ?? undefined,
        },
        type: malformedBitwardenRegistrationCredential.type as PublicKeyCredentialType,
    };
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
 * causing credential login with passkeys to fail when using the Bitwarden browser plugin in Chrome.</p>
 *
 * <p><strong>We fix the issue by creating a serializable copy of the object with its properties converted into the correct format.</strong></p>
 */
export function getLoginCredentialFromMalformedBitwardenObject(
    malformedBitwardenLoginCredential: MalformedBitwardenLoginCredential | null,
): SerializableLoginCredential | undefined {
    if (!malformedBitwardenLoginCredential) {
        return undefined;
    }

    return {
        authenticatorAttachment: malformedBitwardenLoginCredential.authenticatorAttachment as AuthenticatorAttachment,
        clientExtensionResults: malformedBitwardenLoginCredential.getClientExtensionResults() as AuthenticationExtensionsClientOutputs,
        id: malformedBitwardenLoginCredential.id,
        rawId: convertToBase64(malformedBitwardenLoginCredential.rawId),
        response: {
            authenticatorData: convertToBase64(malformedBitwardenLoginCredential.response.authenticatorData),
            clientDataJSON: convertToBase64(malformedBitwardenLoginCredential.response.clientDataJSON),
            signature: convertToBase64(malformedBitwardenLoginCredential.response.signature),
            userHandle: convertToBase64(malformedBitwardenLoginCredential.response.userHandle),
        },
        type: malformedBitwardenLoginCredential.type as PublicKeyCredentialType,
    };
}
