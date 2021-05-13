import { of } from 'rxjs';
import { BuildLogEntry } from 'app/entities/build-log.model';
import { IBuildLogService } from 'app/exercises/programming/shared/service/build-log.service';

export class MockCodeEditorBuildLogService implements IBuildLogService {
    getBuildLogs = () => of([] as BuildLogEntry[]);
    getTestRepositoryBuildLogs = (participationId: number) => of([] as BuildLogEntry[]);
}
