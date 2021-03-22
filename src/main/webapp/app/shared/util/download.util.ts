import { HttpResponse } from '@angular/common/http';

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
    // Different browsers require different code to download file,
    if (window.navigator.appVersion.toString().indexOf('.NET') > 0) {
        // IE & Edge
        window.navigator.msSaveBlob(blob, filename);
    } else {
        // Chrome & FF
        // Create a url and attach file to it,
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
}

export function downloadStream(data: any, type: string) {
    const blob = new Blob([data], { type });
    downloadFile(blob, 'file.pdf');
}
