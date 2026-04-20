import { Injectable } from '@angular/core';
import { BuildPhase, BuildPlanPhases } from 'app/programming/shared/entities/build-plan-phases.model';

@Injectable({ providedIn: 'root' })
export class LegacyBuildPlanConverterService {
    private static readonly LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY = '/var/tmp';

    convertLegacyBuildPlanConfiguration(script: string | undefined, buildPlanConfiguration: string | undefined): BuildPlanPhases | undefined {
        if (script === undefined || buildPlanConfiguration === undefined) {
            return undefined;
        }

        try {
            const parsed = JSON.parse(buildPlanConfiguration);
            if (!this.isObject(parsed)) {
                return undefined;
            }

            const dockerImage = this.parseDockerImage(parsed);
            if (dockerImage === undefined) {
                return undefined;
            }

            const resultPaths = this.parseResultPaths(parsed);
            if (resultPaths === undefined) {
                return undefined;
            }

            return { phases: this.wrapLegacyBuildScript(script, resultPaths), dockerImage: dockerImage };
        } catch {
            return undefined;
        }
    }

    private isObject(value: unknown): value is Record<string, unknown> {
        return typeof value === 'object' && value !== null && value !== undefined;
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

    private parseResultPaths(parsed: Record<string, unknown>): string[] | undefined {
        const actionsNode = parsed['actions'];

        if (!Array.isArray(actionsNode)) {
            return undefined;
        }

        const resultPaths: string[] = [];
        for (const actionNode of actionsNode) {
            if (!this.isObject(actionNode)) {
                return undefined;
            }

            const resultsNode = actionNode['results'];
            if (resultsNode === undefined || resultsNode === null) {
                continue;
            }
            if (!Array.isArray(resultsNode)) {
                return undefined;
            }

            for (const resultNode of resultsNode) {
                if (
                    !this.isObject(resultNode) ||
                    !Object.prototype.hasOwnProperty.call(resultNode, 'path') ||
                    resultNode['path'] === null ||
                    typeof resultNode['path'] !== 'string'
                ) {
                    return undefined;
                }

                resultPaths.push(resultNode['path'].trim());
            }
        }

        return resultPaths;
    }

    private wrapLegacyBuildScript(script: string, resultPaths: string[]): BuildPhase[] {
        const wrappedScript = `cd ${LegacyBuildPlanConverterService.LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY}/testing-dir
local tmp_file=$(mktemp)
cat << '  __LEGACY_INNER_SCRIPT_END__' > "\${tmp_file}"
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
