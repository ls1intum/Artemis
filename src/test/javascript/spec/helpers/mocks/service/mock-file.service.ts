import { of } from 'rxjs';

export class MockFileService {
    downloadFileWithAccessToken = () => {
        return { subscribe: (fn: (value: any) => void) => fn({ body: new Window() }) };
    };

    getTemplateFile = () => {
        return of();
    };
}
