import { Injectable, inject } from '@angular/core';
import { BuildPhase, BuildPlanPhases } from 'app/programming/shared/entities/build-plan-phases.model';
import { ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { BuildPhasesTemplateService } from 'app/programming/shared/services/build-phases-template.service';
import { Observable, map } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class LegacyBuildPlanAdapterService {
    private buildPhasesTemplateService = inject(BuildPhasesTemplateService);

    private static readonly LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY = '/var/tmp';

    extractLegacyDockerImage(buildPlanConfiguration?: string): string | undefined {
        if (!buildPlanConfiguration) {
            return undefined;
        }

        try {
            const parsed = JSON.parse(buildPlanConfiguration);
            const dockerImage = parsed?.metadata?.docker?.image;
            return typeof dockerImage === 'string' && dockerImage.trim().length > 0 ? dockerImage.trim() : undefined;
        } catch {
            return undefined;
        }
    }

    createBuildPhasesFromLegacyBuildScript(
        script: string,
        buildPlanConfiguration: string | undefined,
        programmingLanguage: ProgrammingLanguage,
        projectType?: ProjectType,
        staticCodeAnalysisEnabled?: boolean,
        sequentialTestRuns?: boolean,
    ): Observable<BuildPlanPhases> {
        const dockerImage = this.extractLegacyDockerImage(buildPlanConfiguration);

        return this.buildPhasesTemplateService.getTemplate(programmingLanguage, projectType, staticCodeAnalysisEnabled, sequentialTestRuns).pipe(
            map((templatePhases) => {
                const resultPaths = Array.from(new Set((templatePhases?.phases ?? []).flatMap((phase) => phase.resultPaths ?? [])));
                const phases = this.wrapLegacyBuildScript(script, resultPaths);
                return { phases, dockerImage };
            }),
        );
    }

    private wrapLegacyBuildScript(script: string, resultPaths: string[]): BuildPhase[] {
        const wrappedScript = `cd ${LegacyBuildPlanAdapterService.LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY}/testing-dir
  local tmp_file=$(mktemp)
cat << '__LEGACY_INNER_SCRIPT_END__' > "\${tmp_file}"
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
