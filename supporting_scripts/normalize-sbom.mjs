import { readFileSync, writeFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

// Optional generation identity belongs in release provenance, outside repeatable artifacts.
export function normalizeSbom(bom) {
    const canonicalize = (value) => {
        if (Array.isArray(value)) return value.map(canonicalize);
        if (value === null || typeof value !== 'object') return value;
        return Object.fromEntries(
            Object.keys(value)
                .sort()
                .map((key) => [key, canonicalize(value[key])]),
        );
    };
    const result = canonicalize(bom);
    // Only this BOM and its main component describe the current build environment.
    // Dependency components can carry stable build provenance that must remain intact.
    for (const subject of [result, result.metadata?.component]) {
        if (subject?.externalReferences) subject.externalReferences = subject.externalReferences.filter((reference) => reference.type !== 'build-system');
    }
    delete result.serialNumber;
    if (result.metadata) delete result.metadata.timestamp;
    // cdxgen's optional boilerplate includes both a generation clock and a prose date.
    // Keep other annotations intact: their timestamp is required by CycloneDX.
    if (result.annotations) {
        result.annotations = result.annotations.filter(
            (annotation) =>
                annotation['bom-ref'] !== 'metadata-annotations' || annotation.annotator?.component?.group !== '@cyclonedx' || annotation.annotator?.component?.name !== 'cdxgen',
        );
    }
    return `${JSON.stringify(result, null, 2)}\n`;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
    for (const path of process.argv.slice(2)) writeFileSync(path, normalizeSbom(JSON.parse(readFileSync(path, 'utf8'))));
}
