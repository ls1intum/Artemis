import { of } from 'rxjs';
import { DomainChange, IBuildLogService } from 'app/code-editor';
import { BuildLogEntry } from 'app/entities/build-log';

export class MockCodeEditorBuildLogService implements IBuildLogService {
    getBuildLogs = () => of([] as BuildLogEntry[]);
    setDomain = (domain: DomainChange) => of();
}
