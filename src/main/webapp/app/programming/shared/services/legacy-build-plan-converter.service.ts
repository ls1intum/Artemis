import { Injectable } from '@angular/core';
import { BuildPhase, BuildPlanPhases } from 'app/programming/shared/entities/build-plan-phases.model';
import { parseJson } from 'app/foundation/util/json.util';

/**
 * The purpose of this service is to transform the legacy build plan into the new format so it can still be edited
 * with the new editor.
 */
@Injectable({ providedIn: 'root' })
export class LegacyBuildPlanConverterService {
    private static readonly LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY = '/var/tmp';

    convertLegacyBuildPlanConfiguration(script: string | undefined, buildPlanConfiguration: string | undefined): BuildPlanPhases | undefined {
        let parsed: Record<string, unknown> | undefined;
        const buildScript = script?.trim() ? script : undefined;

        if (buildPlanConfiguration?.trim()) {
            try {
                const parsedJson = parseJson(buildPlanConfiguration);
                if (this.isObject(parsedJson)) {
                    parsed = parsedJson;
                }
            } catch {
                if (buildScript === undefined) {
                    return undefined;
                }
            }
        }

        const actionsNode = parsed?.['actions'];
        if (buildScript === undefined && !Array.isArray(actionsNode)) {
            return undefined;
        }

        const convertedBuildScript = buildScript ?? this.createLegacyBuildScriptFromActions(actionsNode);

        return { phases: this.wrapLegacyBuildScript(convertedBuildScript, this.parseResultPaths(actionsNode)), dockerImage: parsed ? this.parseDockerImage(parsed) : undefined };
    }

    private isObject(value: unknown): value is Record<string, unknown> {
        return typeof value === 'object' && value !== null;
    }

    private parseDockerImage(parsed: Record<string, unknown>): string | undefined {
        const metadata = parsed['metadata'];
        if (!this.isObject(metadata)) {
            return undefined;
        }

        const docker = metadata['docker'];
        if (!this.isObject(docker)) {
            return undefined;
        }

        const dockerImageNode = docker['image'];
        if (typeof dockerImageNode !== 'string') {
            return undefined;
        }

        return dockerImageNode.trim();
    }

    private createLegacyBuildScriptFromActions(actionsNode: unknown): string {
        let buildScript = `#!/bin/bash
cd ${LegacyBuildPlanConverterService.LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY}/testing-dir
`;
        if (!Array.isArray(actionsNode)) {
            return buildScript;
        }

        for (const actionNode of actionsNode) {
            if (!this.isObject(actionNode) || typeof actionNode['script'] !== 'string') {
                continue;
            }

            const workdir = typeof actionNode['workdir'] === 'string' ? actionNode['workdir'].trim() : undefined;
            if (workdir) {
                buildScript += `cd ${LegacyBuildPlanConverterService.LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY}/testing-dir/${workdir}
`;
            }
            buildScript += `${actionNode['script']}
`;
            if (workdir) {
                buildScript += `cd ${LegacyBuildPlanConverterService.LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY}/testing-dir
`;
            }
        }

        return buildScript;
    }

    private parseResultPaths(actionsNode: unknown): string[] {
        if (!Array.isArray(actionsNode)) {
            return [];
        }

        const resultPaths: string[] = [];
        for (const actionNode of actionsNode) {
            if (!this.isObject(actionNode)) {
                continue;
            }

            const resultsNode = actionNode['results'];
            if (resultsNode === undefined || resultsNode === null) {
                continue;
            }
            if (!Array.isArray(resultsNode)) {
                continue;
            }

            for (const resultNode of resultsNode) {
                if (!this.isObject(resultNode) || typeof resultNode['path'] !== 'string') {
                    continue;
                }

                resultPaths.push(resultNode['path'].trim());
            }
        }

        return resultPaths;
    }

    private wrapLegacyBuildScript(script: string, resultPaths: string[]): BuildPhase[] {
        const wrappedScript = `# feel free to remove the code surrounding your script and split your script into multiple phases
cd ${LegacyBuildPlanConverterService.LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY}/testing-dir
local tmp_file=$(mktemp)
cat << '  __LEGACY_INNER_SCRIPT_END__' > "\${tmp_file}"  # two leading spaces are intentional as the final script will be indented be for a phase
${script}
__LEGACY_INNER_SCRIPT_END__
chmod +x "\${tmp_file}"
"\${tmp_file}" "$@"
`;
        return [
            {
                name: 'script',
                script: wrappedScript,
                condition: 'ALWAYS',
                forceRun: false,
                resultPaths,
            },
        ];
    }
}
