import { readFileSync } from 'node:fs';
import { basename, join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { parseArgs } from 'node:util';
import { unzipSync } from 'fflate';

const mib = 1024 ** 2;
const classes = 'WEB-INF/classes/';
const app = classes + 'de/tum/cit/aet/artemis/';
const lib = 'WEB-INF/lib/';
const client = classes + 'static/index.html';
const agent = app + 'buildagent/service/BuildJobExecutionService.class';
const ai = app + 'hyperion/service/worker/HyperionWorkloadService.class';
const workerExclusions = [
    classes + 'static/',
    classes + 'config/liquibase/',
    app + 'admin/web/',
    ...['hibernate-core-', 'liquibase-core-', 'mysql-connector-j-', 'postgresql-', 'jplag-'].map((name) => lib + name),
];

const roles = {
    universal: { required: [client, agent, ai], forbidden: [], budget: Infinity, client: true },
    core: {
        required: [app + 'CoreApp.class', client, classes + 'prompts/hyperion/variants/plan_quiz.st'],
        forbidden: [
            app + 'buildagent/service/',
            app + 'localci/service/scaparser/ReportParser',
            app + 'hyperion/service/worker/',
            lib + 'kubernetes-',
            lib + 'docker-java-core-',
            lib + 'docker-java-transport',
        ],
        budget: 415,
        client: true,
    },
    buildagent: {
        required: [agent, app + 'buildagent/BuildAgentApplication.class', classes + 'config/application-k8s.yml'],
        forbidden: [...workerExclusions, app + 'hyperion/', lib + 'openai-java-', lib + 'plantuml-'],
        budget: 130,
        client: false,
    },
    aiworker: {
        required: [ai, app + 'aiworker/AiWorkerApplication.class'],
        forbidden: [...workerExclusions, app + 'buildagent/service/', lib + 'kubernetes-', lib + 'tomcat-embed-core-'],
        budget: 230,
        client: false,
    },
};

/** Read only the central directory and the SBOM contents. Nested library sizes are their actual contributions to the archive. */
export function verifyArchive(war, role, sbom, universalSize = Infinity) {
    const rules = roles[role];
    const archive = readFileSync(war);
    const limit = Math.min(rules.budget * mib, universalSize - 1);
    if (archive.length > limit) throw new Error(`${war}: ${archive.length} bytes exceeds the limit of ${limit}`);
    const entries = new Set();
    const libraries = [];
    let resources = 0;
    let clientBytes = 0;
    const inventories = unzipSync(archive, {
        filter: ({ name, size }) => {
            if (name.endsWith('/')) return false;
            entries.add(name);
            if (rules.forbidden.some((prefix) => name.startsWith(prefix))) throw new Error(`${war}: unexpected ${name}`);
            if (name.startsWith(lib) && name.endsWith('.jar')) libraries.push([basename(name), size]);
            if (name.startsWith(classes) && !name.endsWith('.class')) resources += size;
            if (name.startsWith(classes + 'static/')) clientBytes += size;
            return sbom !== undefined && name.startsWith(classes + 'sbom/');
        },
    });
    for (const name of [
        ...rules.required,
        'META-INF/NOTICE',
        classes + 'logback-spring.xml',
        classes + 'logback/message-converters.xml',
        app + 'core/config/CrlfEscapingMessageConverter.class',
    ]) {
        if (!entries.has(name)) throw new Error(`${war}: missing ${name}`);
    }
    if (sbom !== undefined) {
        const expected = sbom ? ['server', ...(rules.client ? ['client'] : [])].map((name) => classes + 'sbom/' + name + '-sbom.json') : [];
        for (const name of expected) {
            if (!inventories[name]) throw new Error(`${war}: missing ${name}`);
            const { components } = JSON.parse(new TextDecoder().decode(inventories[name]));
            if (!Array.isArray(components) || !components.length) throw new Error(`${war}: empty inventory in ${name}`);
        }
        for (const name of Object.keys(inventories)) {
            if (!expected.includes(name)) throw new Error(`${war}: unexpected inventory ${name}`);
        }
    }
    const largest = libraries
        .sort((a, b) => b[1] - a[1])
        .slice(0, 5)
        .map(([name, size]) => `${name} (${(size / mib).toFixed(1)} MiB)`);
    console.log(
        `${basename(war)}: ${archive.length.toLocaleString('en-US')} bytes (${(archive.length / mib).toFixed(1)} MiB), ${libraries.length} libraries, ${(resources / mib).toFixed(1)} MiB resources including ${(clientBytes / mib).toFixed(1)} MiB client`,
    );
    console.log('  Largest libraries: ' + largest.join(', '));
    return archive.length;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
    const { positionals, values } = parseArgs({ options: { sbom: { type: 'boolean' } }, allowNegative: true, allowPositionals: true });
    if (positionals.length !== 2) throw new Error('Usage: verify-role-wars.mjs [--sbom|--no-sbom] <universal WAR> <role WAR directory>');
    const [universal, directory] = positionals;
    const original = verifyArchive(universal, 'universal', values.sbom);
    for (const role of ['core', 'buildagent', 'aiworker']) {
        const size = verifyArchive(join(directory, `Artemis-${role}.war`), role, values.sbom, original);
        console.log(`  Saves ${((original - size) / mib).toFixed(1)} MiB (${((1 - size / original) * 100).toFixed(1)}%) against this universal WAR`);
    }
}
