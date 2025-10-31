import { encodeAsBase64Url } from 'app/shared/util/base64.util';
import { Malformed1Password8Credential } from 'app/core/user/settings/passkey-settings/entities/malformed-1password8-credential';

export function getCredentialFromMalformed1Password8Object(malformed1Password8Credential: Malformed1Password8Credential) {
    if (!malformed1Password8Credential) {
        return null;
    }

    // Convert ArrayBuffers to base64url strings for JSON serialization
    const rawIdBase64 = encodeAsBase64Url(new Uint8Array(malformed1Password8Credential.rawId));
    const clientDataJSONBase64 = encodeAsBase64Url(new Uint8Array(malformed1Password8Credential.response.clientDataJSON));
    const attestationObjectBase64 = malformed1Password8Credential.response.attestationObject
        ? encodeAsBase64Url(new Uint8Array(malformed1Password8Credential.response.attestationObject))
        : undefined;
    const registerAuthenticatorDataBase64 = malformed1Password8Credential.response.getAuthenticatorData?.()
        ? encodeAsBase64Url(new Uint8Array(malformed1Password8Credential.response.getAuthenticatorData!()))
        : undefined;
    const loginAuthenticationDataBase64 = malformed1Password8Credential.response.authenticatorData
        ? encodeAsBase64Url(new Uint8Array(malformed1Password8Credential.response.authenticatorData!))
        : undefined;
    const publicKeyBase64 = malformed1Password8Credential.response.getPublicKey?.()
        ? encodeAsBase64Url(new Uint8Array(malformed1Password8Credential.response.getPublicKey!()))
        : undefined;
    const signatureBase64 = malformed1Password8Credential.response.signature ? encodeAsBase64Url(new Uint8Array(malformed1Password8Credential.response.signature)) : undefined;
    const userHandleBase64 = malformed1Password8Credential.response.userHandle ? encodeAsBase64Url(new Uint8Array(malformed1Password8Credential.response.userHandle)) : undefined;

    return {
        authenticatorAttachment: malformed1Password8Credential.authenticatorAttachment,
        clientExtensionResults: malformed1Password8Credential.getClientExtensionResults(),
        id: malformed1Password8Credential.id,
        rawId: rawIdBase64,
        response: {
            attestationObject: attestationObjectBase64,
            authenticatorData: registerAuthenticatorDataBase64 ?? loginAuthenticationDataBase64,
            clientDataJSON: clientDataJSONBase64,
            publicKey: publicKeyBase64,
            publicKeyAlgorithm: malformed1Password8Credential.response.getPublicKeyAlgorithm?.(),
            transports: malformed1Password8Credential.response.getTransports?.(),
            signature: signatureBase64,
            userHandle: userHandleBase64,
        },
        type: malformed1Password8Credential.type,
    };
}
