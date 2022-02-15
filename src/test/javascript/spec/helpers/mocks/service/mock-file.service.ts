import { of } from 'rxjs';

export class MockFileService {
    downloadMergedFileWithAccessToken = () => {
        return { subscribe: (fn: (value: any) => void) => fn({ body: new Window() }) };
    };

    downloadFileWithAccessToken = () => {
        return { subscribe: (fn: (value: any) => void) => fn({ body: new Window() }) };
    };

    getTemplateFile = () => {
        return of();
    };
}
