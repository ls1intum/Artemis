import { Injectable } from '@angular/core';

@Injectable({
    providedIn: 'root',
})
export class CsvDownloadService {
    downloadCSV(content: string, filename: string) {
        const encodedUri = encodeURI(content);
        const link = document.createElement('a');
        link.setAttribute('href', encodedUri);
        link.setAttribute('download', filename);
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
    }
}
