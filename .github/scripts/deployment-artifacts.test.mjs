import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, mkdirSync, renameSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { deployImages, sameTestServerConfiguration, warArtifact } from './deployment-artifacts.mjs';

test('a deployment uses the publication format and Dockerfile of the requested Git object', (t) => {
    const dir = mkdtempSync(join(tmpdir(), 'artemis-deploy-formats-'));
    t.after(() => rmSync(dir, { recursive: true, force: true }));
    const git = (...args) => execFileSync('git', args, { cwd: dir, encoding: 'utf8' }).trim();
    const commit = () => {
        git('add', '.');
        git('-c', 'user.name=fixture', '-c', 'user.email=fixture@example.invalid', '-c', 'commit.gpgsign=false', 'commit', '-qm', 'fixture');
        return git('rev-parse', 'HEAD');
    };
    git('init', '-q');
    mkdirSync(join(dir, 'docker/artemis'), { recursive: true });
    writeFileSync(join(dir, 'docker/artemis/Dockerfile'), 'FROM busybox\n');
    writeFileSync(join(dir, 'docker/test-server-mysql.yml'), 'image: ghcr.io/ls1intum/artemis:latest\n');
    writeFileSync(join(dir, 'build.gradle'), 'version = "10.8"\n');
    const legacy = commit();
    mkdirSync(join(dir, 'deployment'));
    renameSync(join(dir, 'docker'), join(dir, 'deployment/docker'));
    const moved = commit();
    writeFileSync(join(dir, 'deployment/docker/artemis/Dockerfile'), 'ARG ARTEMIS_ROLE=universal\nFROM busybox\n');
    writeFileSync(join(dir, 'deployment/docker/test-server-services.yml'), 'image: ghcr.io/ls1intum/artemis-core:latest\nimage: ghcr.io/ls1intum/artemis-buildagent:latest\n');
    mkdirSync(join(dir, 'gradle'));
    writeFileSync(join(dir, 'gradle/core.gradle'), '');
    const dedicated = commit();
    assert.deepEqual(warArtifact(legacy, dir), { name: 'Artemis.war', legacyFilename: 'Artemis-10.8.war' });
    assert.equal(warArtifact(dedicated, dir).name, 'Artemis-role-wars');
    assert.deepEqual(deployImages(legacy, dir), { file: 'docker/artemis/Dockerfile', build: [{ image: 'artemis', args: '' }], verify: ['artemis'] });
    assert.equal(deployImages(moved, dir).file, 'deployment/docker/artemis/Dockerfile');
    assert.deepEqual(deployImages(dedicated, dir).verify, ['artemis-core', 'artemis-buildagent']);
    assert.equal(deployImages(dedicated, dir).build.length, 4);
    assert.equal(sameTestServerConfiguration(dedicated, dedicated, dir), true);
    assert.equal(sameTestServerConfiguration(moved, dedicated, dir), false);
    writeFileSync(join(dir, 'build.gradle'), 'version = "10.9"\n');
    assert.equal(sameTestServerConfiguration(dedicated, commit(), dir), true);
    // A branch with the new Compose files but an old Dockerfile cannot publish a mislabeled image.
    writeFileSync(join(dir, 'deployment/docker/artemis/Dockerfile'), 'FROM busybox\n');
    assert.throws(() => deployImages(commit(), dir), /Incomplete test-server topology/);
});
