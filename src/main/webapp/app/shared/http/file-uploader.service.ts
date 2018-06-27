import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';

export interface FileUploadResponse {
    path: string;
}

@Injectable()
export class FileUploaderService {
    constructor(private http: HttpClient) {}

    uploadFile(file: Blob | File, fileName?: string): Promise<FileUploadResponse> {
        const formData = new FormData();
        formData.append('file', file, fileName);

        return this.http.post<FileUploadResponse>('/api/fileUpload', formData).toPromise();
    }
}
