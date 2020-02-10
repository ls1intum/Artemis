import { of } from 'rxjs';
import { BuildLogEntry } from 'app/entities/build-log/build-log.model';
import { DomainChange } from 'app/code-editor/service/code-editor-domain.service';
import { IBuildLogService } from 'app/programming-assessment/build-logs/build-log.service';

export class MockCodeEditorBuildLogService implements IBuildLogService {
    getBuildLogs = () => of([] as BuildLogEntry[]);
    getTestRepositoryBuildLogs = (participationId: number) => of([] as BuildLogEntry[]);
}
