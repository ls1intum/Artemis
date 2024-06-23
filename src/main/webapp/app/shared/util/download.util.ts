import { HttpResponse } from '@angular/common/http';
import JSZip from 'jszip';

export function downloadZipFileFromResponse(response: HttpResponse<Blob>): void {
    if (response.body) {
        const zipFile = new Blob([response.body], { type: 'application/zip' });
        downloadFile(zipFile, response.headers.get('filename')!);
    }
}

/**
 * Make a file of given blob and allows user to download it from the browser.
 * @param blob data to be written in file.
 * @param filename suggested to the browser.
 */
export function downloadFile(blob: Blob, filename: string) {
    // Create an url and attach file to it,
    const url = window.URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = filename;
    document.body.appendChild(anchor); // For FF
    // Click the url so that browser shows save file dialog,
    anchor.click();
    document.body.removeChild(anchor);
    window.URL.revokeObjectURL(url);
}

export function downloadStream(data: any, type: string, filename: string) {
    const blob = new Blob([data], { type });
    downloadFile(blob, `${filename || 'file'}.pdf`);
}

export function downloadZipFromFilePromises(zip: JSZip, filePromises: Promise<void | File>[], zipFileName: string) {
    Promise.allSettled(filePromises).then(() => {
        zip.generateAsync({ type: 'blob' })
            .then((zipBlob) => {
                downloadFile(zipBlob, zipFileName + '.zip');
            })
            .catch((error) => {
                throw new Error('Failed to create Zip File', error);
            });
    });
}
