import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { lastValueFrom } from 'rxjs';
import { Observable } from 'rxjs';

import { ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { addPublicFilePrefix } from 'app/app.constants';
import { generateUuid } from 'app/foundation/util/crypto.utils';

@Injectable({ providedIn: 'root' })
export class FileService {
    private http = inject(HttpClient);
    private resourceUrl = 'api/core/files';

    /**
     * Fetches the template file for the given programming language
     * @param {ProgrammingLanguage} language
     * @param {ProjectType} projectType (if available)
     * @returns json test file
     */
    getTemplateFile(language: ProgrammingLanguage, projectType?: ProjectType): Observable<string> {
        const urlParts: string[] = [language];
        if (projectType) {
            urlParts.push(projectType);
        }
        return this.http.get<string>(`${this.resourceUrl}/templates/` + urlParts.join('/'), { responseType: 'text' as 'json' });
    }

    /**
     * Fetches the file from the given path and returns it as a File object with a unique file name.
     * @param filePath path of the file (without API prefix)
     * @param mapOfFiles optional map to check if the generated file name already exists
     */
    async getFile(filePath: string, mapOfFiles?: Map<string, { path?: string; file: File }>): Promise<File> {
        const filePathUrl = addPublicFilePrefix(filePath)!;
        const blob = await lastValueFrom(this.http.get(filePathUrl, { responseType: 'blob' }));
        const file = new File([blob], this.getUniqueFileName(this.getExtension(filePath), mapOfFiles));
        return Promise.resolve(file);
    }

    /**
     * Fetches a file from a fully qualified URL and returns it as a Blob.
     *
     * @param fileUrl URL of the file to fetch
     */
    getBlobFromUrl(fileUrl: string): Observable<Blob> {
        return this.http.get(fileUrl, { responseType: 'blob' });
    }

    /**
     * Fetches the template code of conduct
     * @returns markdown file
     */
    getTemplateCodeOfConduct(): Observable<HttpResponse<string>> {
        return this.http.get<string>(`${this.resourceUrl}/templates/code-of-conduct`, { observe: 'response', responseType: 'text' as 'json' });
    }

    /**
     * Downloads the file from the provided downloadUrl.
     *
     * @param downloadUrl url that is stored in the attachment model
     */
    downloadFile(downloadUrl: string) {
        const downloadUrlComponents = downloadUrl.split('/');
        // take the last element
        const fileName = downloadUrlComponents.pop()!;
        const restOfUrl = downloadUrlComponents.join('/');
        const normalizedDownloadUrl = restOfUrl + '/' + encodeURIComponent(fileName);
        const newWindow = window.open('about:blank');
        newWindow!.location.href = normalizedDownloadUrl;
        return newWindow;
    }

    /**
     * Downloads the file from the provided downloadUrl and the attachment name
     *
     * @param downloadUrl url that is stored in the attachment model
     * @param downloadName the name given to the attachment
     * @param version attachment version used to invalidate cached downloads after a replacement
     */
    downloadFileByAttachmentName(downloadUrl: string, downloadName: string, version?: number) {
        const normalizedDownloadUrl = this.createAttachmentFileUrl(downloadUrl, downloadName, true, version);
        const newWindow = window.open('about:blank');
        newWindow!.location.href = normalizedDownloadUrl;
        return newWindow;
    }

    /**
     * Creates the URL to download a attachment file
     *
     * @param downloadUrl url that is stored in the attachment model
     * @param downloadName the name given to the attachment
     * @param encodeName whether or not to encode the downloadName
     * @param version attachment version used to invalidate cached downloads after a replacement
     */
    createAttachmentFileUrl(downloadUrl: string, downloadName: string, encodeName: boolean, version?: number) {
        const downloadUrlComponents = downloadUrl.split('/');
        // take the last element
        const extension = downloadUrlComponents.pop()!.split('.').pop();
        const restOfUrl = downloadUrlComponents.join('/');
        const encodedDownloadName = encodeName ? encodeURIComponent(downloadName + '.' + extension) : downloadName + '.' + extension;
        const attachmentUrl = restOfUrl + '/' + encodedDownloadName;
        return this.addAttachmentVersionToUrl(attachmentUrl, version);
    }

    /**
     * Adds the attachment version to a URL so replacements use a new browser cache entry.
     *
     * @param attachmentUrl attachment URL to version
     * @param version attachment version used to invalidate cached downloads after a replacement
     */
    addAttachmentVersionToUrl(attachmentUrl: string, version?: number | null): string {
        if (version === undefined || version === null) {
            return attachmentUrl;
        }
        const separator = attachmentUrl.includes('?') ? '&' : '?';
        return `${attachmentUrl}${separator}version=${version}`;
    }

    /**
     * Downloads the merged PDF file.
     *
     * @param lectureId the id of the lecture
     */
    downloadMergedFile(lectureId: number): Observable<HttpResponse<Blob>> {
        return this.http.get(`${this.resourceUrl}/attachments/lectures/${lectureId}/merge-pdf`, {
            observe: 'response',
            responseType: 'blob',
        });
    }

    /**
     * Returns the file extension of the given filename.
     *
     * @param filename the filename
     */
    getExtension(filename: string): string {
        return filename.split('.').pop()!;
    }

    /**
     * Returns a unique file name with the given extension.
     *
     * @param extension the file extension to add
     * @param mapOfFiles optional map to check if the generated file name already exists
     */
    getUniqueFileName(extension: string, mapOfFiles?: Map<string, { path?: string; file: File }>): string {
        let name;
        do {
            name = generateUuid() + '.' + extension;
        } while (mapOfFiles && mapOfFiles.has(name));
        return name;
    }

    /**
     * Removes the prefix from the file name, and replaces underscore with spaces
     * @param link
     */
    replaceAttachmentPrefixAndUnderscores(link: string): string {
        return link.replace(/AttachmentUnit_\d{4}-\d{2}-\d{2}T\d{2}-\d{2}-\d{2}-\d{3}_/, '').replace(/_/g, ' ');
    }

    /**
     * Returns the student version of the given link.
     *
     * @param link the file link
     */
    createStudentLink(link: string): string {
        const lastSlashIndex = link.lastIndexOf('/');
        return `${link.substring(0, lastSlashIndex)}/student${link.substring(lastSlashIndex)}`;
    }
}
