import { of } from 'rxjs';

export class MockFileService {
    downloadMergedFile = () => {
        return { subscribe: (fn: (value: any) => void) => fn({ body: new Window() }) };
    };

    downloadFile = () => {
        return { subscribe: (fn: (value: any) => void) => fn({ body: new Window() }) };
    };

    getTemplateFile = () => {
        return of();
    };
}
