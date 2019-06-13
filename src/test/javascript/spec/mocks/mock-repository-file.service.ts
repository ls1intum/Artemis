import { Injectable } from '@angular/core';
import { IRepositoryFileService } from 'app/entities/repository';
import { Observable, of } from 'rxjs';
import { FileType } from 'app/entities/ace-editor/file-change.model';

export class MockRepositoryFileService implements IRepositoryFileService {
    createFile = (participationId: number, fileName: string) => of();
    createFolder = (participationId: number, folderName: string) => of();
    delete = (participationId: number, fileName: string) => of();
    get = (participationId: number, fileName: string) => of();
    query = (participationId: number) => of({});
    rename = (participationId: number, currentFilePath: string, newFilename: string) => of();
    update = (participationId: number, fileName: string, fileContent: string) => of();
}
