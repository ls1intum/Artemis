import { describe, expect, it } from 'vitest';
import { normalizeSbom } from './normalize-sbom.mjs';

describe('repeatable bundled SBOMs', () => {
    it('removes generation identity while preserving dependency evidence and array order', () => {
        const bom = {
            serialNumber: 'urn:uuid:random',
            metadata: {
                timestamp: '2026-10-16T12:00:00Z',
                component: {
                    externalReferences: [
                        { type: 'build-system', url: 'https://ci/runs/1' },
                        { type: 'vcs', url: 'https://git/revision' },
                    ],
                },
            },
            components: [
                { name: 'z', hashes: [{ alg: 'SHA-256', content: 'abc' }] },
                { name: 'a', properties: [{ name: 'timestamp', value: 'domain data' }] },
            ],
        };
        const result = normalizeSbom(bom);
        expect(JSON.parse(result)).toEqual({
            metadata: { component: { externalReferences: [{ type: 'vcs', url: 'https://git/revision' }] } },
            components: bom.components,
        });
        expect(result).toBe(normalizeSbom({ components: bom.components, metadata: { component: bom.metadata.component, timestamp: 'later' }, serialNumber: 'other' }));
        expect(normalizeSbom(JSON.parse(result))).toBe(result);
    });

    it('accepts SBOMs without optional metadata', () => {
        expect(JSON.parse(normalizeSbom({ components: [], bomFormat: 'CycloneDX' }))).toEqual({ bomFormat: 'CycloneDX', components: [] });
    });

    it('retains dependency build provenance while removing this BOM build environment', () => {
        const dependency = {
            name: 'dependency',
            externalReferences: [{ type: 'build-system', url: 'https://dependency.example/build/42' }],
            components: [{ name: 'nested', externalReferences: [{ type: 'build-system', url: 'https://nested.example/build/7' }] }],
        };
        const bom = {
            externalReferences: [
                { type: 'build-system', url: 'https://ci.example/current-run' },
                { type: 'vcs', url: 'https://git.example/source' },
            ],
            metadata: { component: { name: 'Artemis', components: [dependency] } },
            components: [dependency],
        };

        expect(JSON.parse(normalizeSbom(bom))).toEqual({
            externalReferences: [{ type: 'vcs', url: 'https://git.example/source' }],
            metadata: bom.metadata,
            components: [dependency],
        });
    });

    it('omits cdxgen generation commentary without altering other annotations or their required timestamps', () => {
        const domainAnnotation = { timestamp: '2026-10-16T19:16:22Z', subjects: ['pkg:npm/example@1'], text: 'Reviewed analysis' };
        const generatedAnnotation = {
            'bom-ref': 'metadata-annotations',
            annotator: { component: { group: '@cyclonedx', name: 'cdxgen' } },
            timestamp: '2026-10-16T19:16:22Z',
            text: 'This SBOM was created on Friday, October 16, 2026.',
        };
        expect(JSON.parse(normalizeSbom({ annotations: [generatedAnnotation, domainAnnotation] })).annotations).toEqual([domainAnnotation]);
    });
});
