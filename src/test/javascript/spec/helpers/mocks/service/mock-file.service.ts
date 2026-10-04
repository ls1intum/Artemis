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

    getBlobFromUrl = () => {
        return of(new Blob());
    };

    createAttachmentFileUrl(downloadUrl: string, downloadName: string, encodeName: boolean, version?: number) {
        return this.addAttachmentVersionToUrl('attachments/' + downloadName.replace(' ', '-') + '.pdf', version);
    }

    addAttachmentVersionToUrl(attachmentUrl: string, version?: number | null): string {
        if (version === undefined || version === null) {
            return attachmentUrl;
        }
        const separator = attachmentUrl.includes('?') ? '&' : '?';
        return `${attachmentUrl}${separator}version=${version}`;
    }

    replaceLectureAttachmentPrefixAndUnderscores = (link: string) => link;
    replaceAttachmentPrefixAndUnderscores = (link: string) => link;

    createStudentLink = (link: string) => link;
}
