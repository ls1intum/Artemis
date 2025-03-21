import { of } from 'rxjs';
import { BuildLogEntry } from 'app/entities/programming/build-log.model';
import { IBuildLogService } from 'app/programming/service/build-log.service';

export class MockCodeEditorBuildLogService implements IBuildLogService {
    getBuildLogs = () => of([] as BuildLogEntry[]);
    getTestRepositoryBuildLogs = (participationId: number) => of([] as BuildLogEntry[]);
}
