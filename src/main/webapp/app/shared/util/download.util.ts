import { WindowRef } from 'app/core/websocket/window.service';
import { HttpResponse } from '@angular/common/http';

export function downloadZipFileFromResponse(response: HttpResponse<Blob>, $window: WindowRef): void {
    if (response.body) {
        const zipFile = new Blob([response.body], { type: 'application/zip' });
        const url = $window.nativeWindow.URL.createObjectURL(zipFile);
        const link = document.createElement('a');
        link.setAttribute('href', url);
        link.setAttribute('download', response.headers.get('filename')!);
        document.body.appendChild(link); // Required for FF
        link.click();
        window.URL.revokeObjectURL(url);
    }
}
