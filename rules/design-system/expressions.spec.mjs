import { describe, expect, it } from 'vitest';
import { classSiteVisitors, isClassAttribute, objectEntries, resolveMemberValue, resolveProperty } from './expressions.mjs';

const literal = (value) => ({ type: 'Literal', value });
const object = (...properties) => ({ type: 'ObjectExpression', properties });
const property = (key, value) => ({ type: 'Property', key: literal(key), value: literal(value) });
const spread = (argument) => ({ type: 'SpreadElement', argument });

describe('normalized Angular expression helpers', () => {
    it('retains the reference class-bearing attribute vocabulary', () => {
        for (const name of ['class', 'CLASS', 'styleClass', 'containerClass', 'classNames', 'class:list']) expect(isClassAttribute(name)).toBe(true);
        for (const name of ['style', 'containerClasses', 'class:active']) expect(isClassAttribute(name)).toBe(false);
    });

    it('requires the Angular class-site collector rather than silently falling back to another framework', () => {
        const emit = () => {};
        const options = {};
        const visitors = { Element() {} };
        const context = {
            sourceCode: {
                parserServices: {
                    designSystem: {
                        classSiteVisitors: (owner, settings, callback) => {
                            expect([owner, settings, callback]).toEqual([context, options, emit]);
                            return visitors;
                        },
                    },
                },
            },
        };
        expect(classSiteVisitors(context, options, emit)).toBe(visitors);
        expect(() => classSiteVisitors({ sourceCode: { parserServices: {} } }, options, emit)).toThrow();
    });

    it('flattens readable spreads, keeps numeric keys and respects unknown later writes', () => {
        const unknown = spread({ type: 'AngularDynamicValue' });
        const properties = object(property('color', 'red'), spread(object(property(0, true))), unknown);
        expect(objectEntries(properties)).toEqual([{ key: 'color', value: literal('red') }, { key: '0', value: literal(true) }, { unknown }]);
        expect(resolveProperty(properties, 'color')).toEqual({ value: literal('red'), uncertain: true });
        properties.properties.push(property('color', 'blue'));
        expect(resolveProperty(properties, 'color')).toEqual({ value: literal('blue'), uncertain: false });
    });

    it('only resolves literal object members, never scope lookups for unresolved names', () => {
        const member = { type: 'MemberExpression', computed: false, property: { type: 'Identifier', name: 'width' }, object: object(property('width', '100%')) };
        expect(resolveMemberValue(member)).toEqual({ key: 'width', value: literal('100%') });
        const dynamic = { ...member, object: { type: 'Identifier', name: 'styles' } };
        expect(resolveMemberValue(dynamic)).toEqual({ key: 'width', unresolved: dynamic });
        const uncertain = { ...member, object: object(property('width', '100%'), spread({ type: 'AngularDynamicValue' })) };
        expect(resolveMemberValue(uncertain)).toEqual({ key: 'width', unresolved: uncertain });
    });
});
