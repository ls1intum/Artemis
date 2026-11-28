// Exercise the external playbook interface with local inventory aliases and fixture
// assets. No task connects to a deployment host or installs a service.
import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { createHash } from 'node:crypto';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { createServer } from 'node:http';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { promisify } from 'node:util';

const run = promisify(execFile);
const directory = import.meta.dirname;
const filenames = ['Artemis-core.war', 'Artemis-buildagent.war', 'Artemis.war'];

test('select and verify systemd WARs before entering the external update playbook', async (t) => {
    const dir = await mkdtemp(join(tmpdir(), 'artemis-role-wars-'));
    t.after(() => rm(dir, { recursive: true, force: true }));
    await writeFile(join(dir, 'ansible.cfg'), '[defaults]\n');

    const manifest = filenames.map((name) => createHash('sha256').update(name).digest('hex') + '  ' + name).join('\n') + '\n';
    for (const name of filenames) await writeFile(join(dir, name), name);
    await writeFile(join(dir, 'SHA256SUMS'), manifest);

    let base;
    const server = createServer((request, response) => {
        if (request.url === '/SHA256SUMS') return response.end(manifest);
        response.setHeader('Content-Type', 'application/json');
        response.end(
            JSON.stringify({
                assets: filenames.map((name) => ({
                    name,
                    size: name.length,
                    browser_download_url: base + '/' + name,
                    digest: 'sha256:' + createHash('sha256').update(name).digest('hex'),
                })),
            }),
        );
    });
    await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
    t.after(() => new Promise((resolve) => server.close(resolve)));
    base = 'http://127.0.0.1:' + server.address().port;

    const playbook = join(dir, 'external.yml');
    const inventory = join(dir, 'hosts');
    const marker = join(dir, 'called');
    async function setup(cluster, malformed = '') {
        const core = cluster + '_core';
        const agent = cluster + (cluster === 'artemis_production' ? '_buildagent' : '_agents');
        await writeFile(inventory, `[${core}]\ncore1\n[${agent}]\nagent1\n${malformed}\n[${cluster}_nodes:children]\n${core}\n${agent}\n[all:vars]\nansible_connection=local\n`);
        await writeFile(
            playbook,
            `
- hosts: ${cluster}_nodes
  gather_facts: false
  vars:
    artemis_version:
    expected: "{{ 'Artemis.war' if artemis_universal_rollback | default(false) | bool else 'Artemis-' ~ ('core' if inventory_hostname == 'core1' else 'buildagent') ~ '.war' }}"
  tasks:
    - ansible.builtin.assert:
        that:
          - artemis_version == (artemis_release if artemis_release is defined else artemis_war_directory ~ '/' ~ expected)
          - artemis_release is not defined or artemis_war_url == artemis_release_base ~ '/' ~ expected
    - ansible.builtin.copy:
        content: reached
        dest: "${marker}"
      run_once: true
`,
        );
    }
    async function deploy(cluster, inputs) {
        await run(
            'ansible-playbook',
            [
                '-i',
                inventory,
                join(directory, 'deploy-role-wars.yml'),
                '-e',
                '@' + join(directory, 'role-wars.yml'),
                '-e',
                JSON.stringify({
                    artemis_cluster: cluster,
                    artemis_update_playbook: playbook,
                    artemis_release_base: base,
                    artemis_release_metadata_url: base + '/release',
                    ...inputs,
                }),
            ],
            { cwd: dir, env: { ...process.env, ANSIBLE_CONFIG: join(dir, 'ansible.cfg') } },
        );
    }
    for (const cluster of ['artemis_staging1', 'artemis_staging2', 'artemis_production']) {
        await setup(cluster);
        await deploy(cluster, { artemis_war_directory: dir });
    }
    await deploy('artemis_production', { artemis_war_directory: dir, artemis_universal_rollback: true });
    await deploy('artemis_production', { artemis_release: '10.9' });
    await deploy('artemis_production', { artemis_release: '10.8', artemis_universal_rollback: true });
    assert.equal(await readFile(marker, 'utf8'), 'reached');
    await rm(marker);

    async function rejectsBeforeRestart(inputs) {
        await assert.rejects(deploy('artemis_production', inputs));
        await assert.rejects(readFile(marker), { code: 'ENOENT' });
    }
    await setup('artemis_production', 'core1');
    await rejectsBeforeRestart({ artemis_war_directory: dir });
    await setup('artemis_production');
    await writeFile(join(dir, 'Artemis-core.war'), 'wrong bytes');
    await rejectsBeforeRestart({ artemis_war_directory: dir });
    await rm(join(dir, 'Artemis-buildagent.war'));
    await rejectsBeforeRestart({ artemis_war_directory: dir });
});
