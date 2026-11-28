import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';

const directory = import.meta.dirname;
const tag = 'sha-' + 'b'.repeat(40);

function render(stack, env) {
    return JSON.parse(
        execFileSync('docker', ['compose', '--env-file', '../../.env', '-f', stack + '.yml', 'config', '--format', 'json'], {
            cwd: directory,
            env: { ...process.env, ...env, ARTEMIS_DOCKER_TAG: tag },
            encoding: 'utf8',
        }),
    ).services;
}

test('test servers render role-specific images and profiles with Ansible overrides', (t) => {
    const dir = mkdtempSync(join(tmpdir(), 'artemis-test-server-'));
    t.after(() => rmSync(dir, { recursive: true, force: true }));
    const env = {
        ARTEMIS_ENV_FILE: join(dir, 'artemis.env'),
        ARTEMIS_NODE_1_ENV_FILE: join(dir, 'node1.env'),
        ARTEMIS_NODE_2_ENV_FILE: join(dir, 'node2.env'),
        NGINX_PUBLIC_HOSTNAME: 'artemis-test.example',
        REDIS_PASSWORD: 'fixture-password',
        DOCKER_GROUP_ID: '1009',
    };
    for (const nodeFile of [env.ARTEMIS_NODE_1_ENV_FILE, env.ARTEMIS_NODE_2_ENV_FILE]) {
        writeFileSync(nodeFile, "SPRING_PROFILES_ACTIVE='prod,buildagent,core,localvc,localci,docker'\n");
    }
    for (const db of ['postgresql', 'mysql']) {
        for (const kind of ['jenkins', 'localci', 'multi-node']) {
            const localci = kind !== 'jenkins';
            const multi = kind === 'multi-node';
            const profiles = localci ? 'prod,core,localci,localvc,buildagent,docker' : 'prod,core,jenkins,localvc,docker';
            writeFileSync(
                env.ARTEMIS_ENV_FILE,
                `SPRING_PROFILES_ACTIVE: '${profiles}'\nSPRING_DATASOURCE_PASSWORD='fixture-db-secret'\nARTEMIS_CONTINUOUSINTEGRATION_CONCURRENTBUILDSIZE='3'\nARTEMIS_CONTINUOUSINTEGRATION_IMAGECLEANUP_ENABLED='true'\nARTEMIS_CONTINUOUSINTEGRATION_PROXIES_DEFAULT_HTTPSPROXY='http://runner-proxy:3128'\n`,
            );
            const topology = execFileSync('bash', [join(directory, 'prepare-test-server-agent.sh'), env.ARTEMIS_ENV_FILE, '--detect-topology'], { encoding: 'utf8' });
            assert.equal(topology.trim(), localci ? 'localci' : 'core');
            if (localci) assert(!readFileSync(env.ARTEMIS_ENV_FILE + '.runner.env', 'utf8').includes('fixture-db-secret'));
            const stack = 'test-server-' + (multi ? 'multi-node-' : '') + db + (localci ? '-localci' : '');
            const services = render(stack, env);
            const cores = Object.entries(services).filter(([, service]) => service.image === 'ghcr.io/ls1intum/artemis-core:' + tag);
            assert.equal(cores.length, multi ? 2 : 1, stack);
            assert.equal('artemis-app-aiworker' in services, false);
            const agent = services['artemis-app-buildagent'];
            assert.equal(Boolean(agent), localci);
            for (const [serviceName, core] of cores) {
                assert.equal(core.build.args.ARTEMIS_ROLE, 'core');
                assert.equal(core.environment.SPRING_DATASOURCE_PASSWORD, 'fixture-db-secret');
                assert(!core.environment.SPRING_PROFILES_ACTIVE.split(',').includes('buildagent'));
                assert(!core.volumes.some(({ target }) => target.includes('docker.sock')));
                assert(core.healthcheck.test.join(' ').includes('/management/health/readiness'));
                assert(services.nginx.depends_on[serviceName]);
                if (localci) {
                    assert.equal(core.environment.ARTEMIS_VERSIONCONTROL_BUILDAGENTUSESSH, 'true');
                    assert.equal(core.environment.ARTEMIS_DISTRIBUTEDDATA_PROVIDER, agent.environment.ARTEMIS_DISTRIBUTEDDATA_PROVIDER);
                    if (core.environment.ARTEMIS_DISTRIBUTEDDATA_PROVIDER === 'Redis') {
                        assert.equal(core.environment.SPRING_DATA_REDIS_PASSWORD, env.REDIS_PASSWORD);
                        assert.equal(agent.environment.SPRING_DATA_REDIS_PASSWORD, env.REDIS_PASSWORD);
                        assert.notEqual(core.environment.SPRING_DATA_REDIS_CLIENTNAME, agent.environment.SPRING_DATA_REDIS_CLIENTNAME);
                    }
                }
            }
            if (localci) {
                assert.equal(agent.image, 'ghcr.io/ls1intum/artemis-buildagent:' + tag);
                assert.equal(agent.build.args.ARTEMIS_ROLE, 'buildagent');
                assert.equal(agent.environment.SPRING_PROFILES_ACTIVE, 'prod,buildagent');
                assert.equal(agent.environment.SPRING_DATASOURCE_PASSWORD, undefined);
                assert.equal(agent.environment.ARTEMIS_CONTINUOUSINTEGRATION_CONCURRENTBUILDSIZE, '3');
                assert.equal(agent.environment.ARTEMIS_CONTINUOUSINTEGRATION_IMAGECLEANUP_ENABLED, 'true');
                assert.equal(agent.environment.ARTEMIS_CONTINUOUSINTEGRATION_PROXIES_DEFAULT_HTTPSPROXY, 'http://runner-proxy:3128');
                assert.equal(agent.environment.ARTEMIS_VERSIONCONTROL_SSHTEMPLATECLONEURL, 'ssh://git@' + env.NGINX_PUBLIC_HOSTNAME + ':7921/');
                assert(services.nginx.networks.artemis.aliases.includes(env.NGINX_PUBLIC_HOSTNAME));
                assert.equal(agent.volumes.find(({ target }) => target === '/opt/artemis/data').type, 'volume');
                assert(agent.volumes.some(({ target }) => target === '/var/run/docker.sock'));
                assert(agent.group_add.includes(env.DOCKER_GROUP_ID));
                assert(agent.healthcheck.test.join(' ').includes('/management/health/readiness'));
            }
        }
    }
});

test('worker host mounts its own configuration and daemon', () => {
    const env = { AI_WORKER_DOCKER_GID: '1022', AI_WORKER_CONFIG: '/etc/worker.yml', AI_WORKER_DOCKER_SOCKET: '/run/worker/docker.sock' };
    const services = render('test-server-aiworker', env);
    assert.deepEqual(Object.keys(services), ['artemis-app-aiworker']);
    const worker = services['artemis-app-aiworker'];
    assert.equal(worker.image, 'ghcr.io/ls1intum/artemis-aiworker:' + tag);
    assert.equal(worker.environment.SPRING_PROFILES_ACTIVE, 'prod,aiworker');
    assert.equal(worker.healthcheck.disable, true);
    assert.equal(worker.volumes.find(({ source }) => source === env.AI_WORKER_CONFIG).read_only, true);
    assert.equal(worker.volumes.find(({ target }) => target === '/var/run/docker.sock').source, env.AI_WORKER_DOCKER_SOCKET);
    assert.equal(worker.ports, undefined);
});
