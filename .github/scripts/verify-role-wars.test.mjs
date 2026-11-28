import assert from 'node:assert/strict';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { strToU8, zipSync } from 'fflate';
import { verifyArchive } from './verify-role-wars.mjs';

test('dedicated agent requires its runner configuration, logging rules and release inventory', (t) => {
    const dir = mkdtempSync(join(tmpdir(), 'role-war-'));
    t.after(() => rmSync(dir, { recursive: true, force: true }));
    const war = join(dir, 'agent.war');
    const classes = 'WEB-INF/classes/';
    const pkg = classes + 'de/tum/cit/aet/artemis/';
    const entries = Object.fromEntries(
        [
            'META-INF/NOTICE',
            classes + 'logback-spring.xml',
            classes + 'logback/message-converters.xml',
            pkg + 'core/config/CrlfEscapingMessageConverter.class',
            pkg + 'buildagent/service/BuildJobExecutionService.class',
            pkg + 'buildagent/BuildAgentApplication.class',
            classes + 'config/application-k8s.yml',
        ].map((name) => [name, strToU8('fixture')]),
    );
    function verify(sbom, limit) {
        writeFileSync(war, zipSync(entries));
        return verifyArchive(war, 'buildagent', sbom, limit);
    }
    const bytes = verify(false);
    assert.throws(() => verify(false, bytes), /exceeds the limit/);
    assert.throws(() => verify(true), /missing .*server-sbom/);
    const server = classes + 'sbom/server-sbom.json';
    entries[server] = strToU8('{"components":[]}');
    assert.throws(() => verify(true), /empty inventory/);
    entries[server] = strToU8('{"components":[{"name":"fixture"}]}');
    assert(verify(true) > bytes);
    assert.throws(() => verify(false), /unexpected inventory/);
    entries['WEB-INF/lib/openai-java-1.jar'] = strToU8('unrelated');
    assert.throws(() => verify(true), /unexpected .*openai-java/);
    delete entries['WEB-INF/lib/openai-java-1.jar'];
    delete entries[classes + 'config/application-k8s.yml'];
    assert.throws(() => verify(true), /missing .*application-k8s/);
});
