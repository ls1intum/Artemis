import { of } from 'rxjs';

export class MockFileService {
    downloadMergedFile = () => {
        return of({ body: null });
    };

    downloadFile = () => {
        return { subscribe: (fn: (value: any) => void) => fn({ body: new Window() }) };
    };
    downloadFileByAttachmentName = () => {
        return { subscribe: (fn: (value: any) => void) => fn({ body: new Window() }) };
    };

    getTemplateFile = () => {
        return of();
    };

    replaceLectureAttachmentPrefixAndUnderscores = (link: string) => link;
    replaceAttachmentPrefixAndUnderscores = (link: string) => link;
}
