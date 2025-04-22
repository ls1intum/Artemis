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

    createAttachmentFileUrl(downloadUrl: string, downloadName: string, encodeName: boolean) {
        return 'attachments/' + downloadName.replace(' ', '-') + '.pdf';
    }

    replaceLectureAttachmentPrefixAndUnderscores = (link: string) => link;
    replaceAttachmentPrefixAndUnderscores = (link: string) => link;

    createStudentLink = (link: string) => link;
}
