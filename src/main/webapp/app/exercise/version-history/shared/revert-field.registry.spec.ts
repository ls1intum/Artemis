import { describe, expect, it } from 'vitest';

import { REVERTABLE_FIELDS, getRevertConfig, isRevertable } from './revert-field.registry';

describe('revert-field.registry', () => {
    describe('isRevertable', () => {
        it.each(['title', 'maxPoints', 'problemStatement', 'showTestNamesToStudents'])('should return true for registered field "%s"', (fieldId) => {
            expect(isRevertable(fieldId)).toBe(true);
        });

        it.each(['channelName', 'dueDate', 'nonExistentField'])('should return false for unregistered field "%s"', (fieldId) => {
            expect(isRevertable(fieldId)).toBe(false);
        });
    });

    describe('getRevertConfig', () => {
        it('should return correct config for title', () => {
            const config = getRevertConfig('title');
            expect(config).toEqual({
                entityPath: 'title',
                updateStrategy: 'full',
                valueType: 'string',
            });
        });

        it('should return correct config for problemStatement with problemStatement strategy', () => {
            const config = getRevertConfig('problemStatement');
            expect(config).toEqual({
                entityPath: 'problemStatement',
                updateStrategy: 'problemStatement',
                valueType: 'string',
            });
        });

        it('should return correct config for maxPoints with number valueType', () => {
            const config = getRevertConfig('maxPoints');
            expect(config).toEqual({
                entityPath: 'maxPoints',
                updateStrategy: 'full',
                valueType: 'number',
            });
        });

        it('should return undefined for unregistered field', () => {
            expect(getRevertConfig('nonExistentField')).toBeUndefined();
        });
    });

    describe('REVERTABLE_FIELDS map', () => {
        const validStrategies = ['full', 'timeline', 'problemStatement'];
        const validValueTypes = ['string', 'number', 'boolean', 'date'];

        it('should have 10 entries', () => {
            expect(REVERTABLE_FIELDS.size).toBe(10);
        });

        it('should have valid updateStrategy for all entries', () => {
            for (const [, config] of REVERTABLE_FIELDS) {
                expect(validStrategies).toContain(config.updateStrategy);
            }
        });

        it('should have valid valueType for all entries', () => {
            for (const [, config] of REVERTABLE_FIELDS) {
                expect(validValueTypes).toContain(config.valueType);
            }
        });
    });
});
