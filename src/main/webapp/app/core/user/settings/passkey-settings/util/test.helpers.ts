import { expect } from '@jest/globals';
import { SerializableRegistrationCredential } from 'app/core/user/settings/passkey-settings/entities/serializable-registration-credential';
import { SerializableLoginCredential } from 'app/core/user/settings/passkey-settings/entities/serializable-login-credential';

/**
 * Validates that specified fields in a registration credential contain valid base64url strings.
 * Base64url strings should only contain A-Z, a-z, 0-9, -, and _ characters.
 *
 * @param credential The credential object to validate
 * @param fields Array of field paths to check (e.g., ['rawId', 'response.clientDataJSON'])
 */
export function expectBase64UrlFieldsForRegistration(credential: SerializableRegistrationCredential | undefined, fields: string[]): void {
    const base64UrlPattern = /^[A-Za-z0-9_-]+$/;

    fields.forEach((fieldPath) => {
        const value = getNestedValue(credential, fieldPath);

        expect(value).toMatch(base64UrlPattern);
    });
}

/**
 * Validates that specified fields in a login credential contain valid base64url strings.
 * Base64url strings should only contain A-Z, a-z, 0-9, -, and _ characters.
 *
 * @param credential The credential object to validate
 * @param fields Array of field paths to check (e.g., ['rawId', 'response.clientDataJSON'])
 */
export function expectBase64UrlFieldsForLogin(credential: SerializableLoginCredential | undefined, fields: string[]): void {
    const base64UrlPattern = /^[A-Za-z0-9_-]+$/;

    fields.forEach((fieldPath) => {
        const value = getNestedValue(credential, fieldPath);

        expect(value).toMatch(base64UrlPattern);
    });
}

/**
 * Helper function to get nested property value from an object using dot notation.
 * E.g., 'response.clientDataJSON' will access credential.response.clientDataJSON
 */
function getNestedValue(obj: any, path: string): any {
    return path.split('.').reduce((current, key) => current?.[key], obj);
}
