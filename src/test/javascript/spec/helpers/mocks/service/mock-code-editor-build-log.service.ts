import { BuildLogEntry } from 'app/entities/build-log.model';
import { IBuildLogService } from 'app/exercises/programming/shared/service/build-log.service';
import { of } from 'rxjs';

export class MockCodeEditorBuildLogService implements IBuildLogService {
    getBuildLogs = () => of([] as BuildLogEntry[]);
    getTestRepositoryBuildLogs = (participationId: number) => of([] as BuildLogEntry[]);
}
