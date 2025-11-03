import { encodeAsBase64Url } from 'app/shared/util/base64.util';
import { Malformed1password8RegistrationCredential } from 'app/core/user/settings/passkey-settings/entities/malformed-1password8-registration-credential';
import { SerializableRegistrationCredential } from 'app/core/user/settings/passkey-settings/entities/serializable-registration-credential';
import { SerializableLoginCredential } from 'app/core/user/settings/passkey-settings/entities/serializable-login-credential';
import { Malformed1Password8LoginCredential } from 'app/core/user/settings/passkey-settings/entities/malformed-1password8-login-credential';

export function getRegistrationCredentialFromMalformed1Password8Object(
    malformed1Password8RegistrationCredential: Malformed1password8RegistrationCredential | null,
): SerializableRegistrationCredential | undefined {
    if (!malformed1Password8RegistrationCredential) {
        return undefined;
    }

    // Convert ArrayBuffers to base64url strings for JSON serialization
    const rawIdBase64 = encodeAsBase64Url(new Uint8Array(malformed1Password8RegistrationCredential.rawId));
    const clientDataJSONBase64 = encodeAsBase64Url(new Uint8Array(malformed1Password8RegistrationCredential.response.clientDataJSON));
    const attestationObjectBase64 = malformed1Password8RegistrationCredential.response.attestationObject
        ? encodeAsBase64Url(new Uint8Array(malformed1Password8RegistrationCredential.response.attestationObject))
        : undefined;
    const authenticatorDataBase64 = malformed1Password8RegistrationCredential.response.getAuthenticatorData?.()
        ? encodeAsBase64Url(new Uint8Array(malformed1Password8RegistrationCredential.response.getAuthenticatorData!()))
        : undefined;
    const publicKeyBase64 = malformed1Password8RegistrationCredential.response.getPublicKey?.()
        ? encodeAsBase64Url(new Uint8Array(malformed1Password8RegistrationCredential.response.getPublicKey!()))
        : undefined;

    return {
        authenticatorAttachment: malformed1Password8RegistrationCredential.authenticatorAttachment as AuthenticatorAttachment,
        clientExtensionResults: malformed1Password8RegistrationCredential.getClientExtensionResults() as AuthenticationExtensionsClientOutputs,
        id: malformed1Password8RegistrationCredential.id,
        rawId: rawIdBase64,
        response: {
            attestationObject: attestationObjectBase64,
            authenticatorData: authenticatorDataBase64,
            clientDataJSON: clientDataJSONBase64,
            publicKey: publicKeyBase64,
            publicKeyAlgorithm: malformed1Password8RegistrationCredential.response.getPublicKeyAlgorithm?.(),
            transports: malformed1Password8RegistrationCredential.response.getTransports?.(),
        },
        type: malformed1Password8RegistrationCredential.type as PublicKeyCredentialType,
    };
}

export function getLoginCredentialFromMalformed1Password8Object(
    malformed1Password8LoginCredential: Malformed1Password8LoginCredential | null,
): SerializableLoginCredential | undefined {
    if (!malformed1Password8LoginCredential) {
        return undefined;
    }

    // Convert ArrayBuffers to base64url strings for JSON serialization
    const rawIdBase64 = encodeAsBase64Url(new Uint8Array(malformed1Password8LoginCredential.rawId));
    const clientDataJSONBase64 = encodeAsBase64Url(new Uint8Array(malformed1Password8LoginCredential.response.clientDataJSON));
    const authenticatorDataBase64 = malformed1Password8LoginCredential.response.authenticatorData
        ? encodeAsBase64Url(new Uint8Array(malformed1Password8LoginCredential.response.authenticatorData!))
        : undefined;
    const signatureBase64 = malformed1Password8LoginCredential.response.signature
        ? encodeAsBase64Url(new Uint8Array(malformed1Password8LoginCredential.response.signature))
        : undefined;
    const userHandleBase64 = malformed1Password8LoginCredential.response.userHandle
        ? encodeAsBase64Url(new Uint8Array(malformed1Password8LoginCredential.response.userHandle))
        : undefined;

    return {
        authenticatorAttachment: malformed1Password8LoginCredential.authenticatorAttachment as AuthenticatorAttachment,
        clientExtensionResults: malformed1Password8LoginCredential.getClientExtensionResults() as AuthenticationExtensionsClientOutputs,
        id: malformed1Password8LoginCredential.id,
        rawId: rawIdBase64,
        response: {
            authenticatorData: authenticatorDataBase64,
            clientDataJSON: clientDataJSONBase64,
            signature: signatureBase64,
            userHandle: userHandleBase64,
        },
        type: malformed1Password8LoginCredential.type as PublicKeyCredentialType,
    };
}
