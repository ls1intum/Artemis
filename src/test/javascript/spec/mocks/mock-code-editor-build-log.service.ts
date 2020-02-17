import { of } from 'rxjs';
import { BuildLogEntry } from 'app/entities/build-log/build-log.model';
import { IBuildLogService } from 'app/programming-assessment/build-logs/build-log.service';
import { DomainChange } from 'app/code-editor/model/code-editor.model';

export class MockCodeEditorBuildLogService implements IBuildLogService {
    getBuildLogs = () => of([] as BuildLogEntry[]);
    getTestRepositoryBuildLogs = (participationId: number) => of([] as BuildLogEntry[]);
}
