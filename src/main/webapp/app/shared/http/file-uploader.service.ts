import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';

export interface FileUploadResponse {
    path: string;
}

@Injectable()
export class FileUploaderService {
    constructor(private http: HttpClient) { }

    uploadFile(file: Blob | File, fileName?: string): Promise<FileUploadResponse> {
        const formData = new FormData();
        formData.append('file', file, fileName);
        return this.http.post<FileUploadResponse>('/api/fileUpload', formData).toPromise();
    }

    /**
     * Duplicates file in the backend.
     * @param filePath Path of the file which needs to be duplicated
     */
    async duplicateFile(filePath: string): Promise<FileUploadResponse> {
        const file = await this.http.get(filePath, { responseType: 'blob' }).toPromise();
        const tempFilename = 'temp' + filePath.split('/').pop().split('#')[0].split('?')[0];
        const formData = new FormData();
        formData.append('file', file, tempFilename);
        return await this.http.post<FileUploadResponse>('/api/fileUpload', formData).toPromise();
    }
}
