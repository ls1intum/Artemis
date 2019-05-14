import { Observable } from 'rxjs';
import { IBuildLogService } from 'app/code-editor';
import { BuildLogEntryArray } from 'app/entities/build-log';

export class MockCodeEditorBuildLogService implements IBuildLogService {
    getBuildLogs = () => Observable.of(new BuildLogEntryArray());
}
