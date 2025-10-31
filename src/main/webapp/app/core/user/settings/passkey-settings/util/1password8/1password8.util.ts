import { encodeAsBase64Url } from 'app/shared/util/base64.util';
import { Malformed1Password8Credential } from 'app/core/user/settings/passkey-settings/entities/malformed-1password8-credential';
import { SerializableRegistrationCredential } from 'app/core/user/settings/passkey-settings/entities/serializable-registration-credential';
import { SerializableLoginCredential } from 'app/core/user/settings/passkey-settings/entities/serializable-login-credential';

export function getRegistrationCredentialFromMalformed1Password8Object(
    malformed1Password8Credential: Malformed1Password8Credential | null,
): SerializableRegistrationCredential | undefined {
    if (!malformed1Password8Credential) {
        return undefined;
    }

    // Convert ArrayBuffers to base64url strings for JSON serialization
    const rawIdBase64 = encodeAsBase64Url(new Uint8Array(malformed1Password8Credential.rawId));
    const clientDataJSONBase64 = encodeAsBase64Url(new Uint8Array(malformed1Password8Credential.response.clientDataJSON));
    const attestationObjectBase64 = malformed1Password8Credential.response.attestationObject
        ? encodeAsBase64Url(new Uint8Array(malformed1Password8Credential.response.attestationObject))
        : undefined;
    const authenticatorDataBase64 = malformed1Password8Credential.response.getAuthenticatorData?.()
        ? encodeAsBase64Url(new Uint8Array(malformed1Password8Credential.response.getAuthenticatorData!()))
        : undefined;
    const publicKeyBase64 = malformed1Password8Credential.response.getPublicKey?.()
        ? encodeAsBase64Url(new Uint8Array(malformed1Password8Credential.response.getPublicKey!()))
        : undefined;

    return {
        authenticatorAttachment: malformed1Password8Credential.authenticatorAttachment as AuthenticatorAttachment,
        clientExtensionResults: malformed1Password8Credential.getClientExtensionResults() as AuthenticationExtensionsClientOutputs,
        id: malformed1Password8Credential.id,
        rawId: rawIdBase64,
        response: {
            attestationObject: attestationObjectBase64,
            authenticatorData: authenticatorDataBase64,
            clientDataJSON: clientDataJSONBase64,
            publicKey: publicKeyBase64,
            publicKeyAlgorithm: malformed1Password8Credential.response.getPublicKeyAlgorithm?.(),
            transports: malformed1Password8Credential.response.getTransports?.(),
        },
        type: malformed1Password8Credential.type as PublicKeyCredentialType,
    };
}

export function getLoginCredentialFromMalformed1Password8Object(malformed1Password8Credential: Malformed1Password8Credential | null): SerializableLoginCredential | undefined {
    if (!malformed1Password8Credential) {
        return undefined;
    }

    // Convert ArrayBuffers to base64url strings for JSON serialization
    const rawIdBase64 = encodeAsBase64Url(new Uint8Array(malformed1Password8Credential.rawId));
    const clientDataJSONBase64 = encodeAsBase64Url(new Uint8Array(malformed1Password8Credential.response.clientDataJSON));
    const authenticatorDataBase64 = malformed1Password8Credential.response.authenticatorData
        ? encodeAsBase64Url(new Uint8Array(malformed1Password8Credential.response.authenticatorData!))
        : undefined;
    const signatureBase64 = malformed1Password8Credential.response.signature ? encodeAsBase64Url(new Uint8Array(malformed1Password8Credential.response.signature)) : undefined;
    const userHandleBase64 = malformed1Password8Credential.response.userHandle ? encodeAsBase64Url(new Uint8Array(malformed1Password8Credential.response.userHandle)) : undefined;

    return {
        authenticatorAttachment: malformed1Password8Credential.authenticatorAttachment as AuthenticatorAttachment,
        clientExtensionResults: malformed1Password8Credential.getClientExtensionResults() as AuthenticationExtensionsClientOutputs,
        id: malformed1Password8Credential.id,
        rawId: rawIdBase64,
        response: {
            authenticatorData: authenticatorDataBase64,
            clientDataJSON: clientDataJSONBase64,
            signature: signatureBase64,
            userHandle: userHandleBase64,
        },
        type: malformed1Password8Credential.type as PublicKeyCredentialType,
    };
}
