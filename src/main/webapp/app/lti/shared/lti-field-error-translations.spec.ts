import { describe, expect, it } from 'vitest';
import enLti from '../../../i18n/en/lti.json';
import deLti from '../../../i18n/de/lti.json';

/**
 * A rejected LTI configuration request returns field errors. The alert service translates the name of the offending field as
 * `artemisApp.<object name>.<field>`, where the object name is the name of the server DTO without its `DTO` suffix.
 * Without these keys the alert shows `translation-not-found[artemisApp.onlineCourseConfiguration.userPrefix]` instead of the field name.
 */
describe('LTI field error translations', () => {
    const fieldsByObjectName: Record<string, string[]> = {
        onlineCourseConfiguration: ['userPrefix', 'requireExistingUser'],
        ltiPlatformConfigurationUpdate: ['registrationId', 'clientId', 'originalUrl', 'customName', 'authorizationUri', 'jwkSetUri', 'tokenUri'],
    };

    const translationsByLanguage = { en: enLti.artemisApp, de: deLti.artemisApp } as unknown as Record<string, Record<string, Record<string, unknown>>>;

    for (const [language, translations] of Object.entries(translationsByLanguage)) {
        it(`should translate the client side validation messages in ${language}`, () => {
            const lti = translations['lti'] as Record<string, Record<string, Record<string, Record<string, unknown>>>>;
            // the course form shows 'artemisApp.lti.edit.form.userPrefix.<validator error key>' for each failed validator
            expect(lti['edit']['form']['userPrefix']['forbidden']).toBeTypeOf('string');
            expect(lti['edit']['form']['userPrefix']['maxlength']).toBeTypeOf('string');
            expect(lti['validation']['customNameRequired']).toBeTypeOf('string');
        });

        for (const [objectName, fields] of Object.entries(fieldsByObjectName)) {
            it(`should translate every field of ${objectName} in ${language}`, () => {
                for (const field of fields) {
                    expect(translations[objectName]?.[field], `artemisApp.${objectName}.${field}`).toBeTypeOf('string');
                }
            });
        }
    }
});
