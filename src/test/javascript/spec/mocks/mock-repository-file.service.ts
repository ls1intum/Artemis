import { IRepositoryFileService } from 'app/entities/repository';
import { of } from 'rxjs';

export class MockRepositoryFileService implements IRepositoryFileService {
    createFile = (participationId: number, fileName: string) => of();
    createFolder = (participationId: number, folderName: string) => of();
    delete = (participationId: number, fileName: string) => of();
    get = (participationId: number, fileName: string) => of();
    query = (participationId: number) => of({});
    rename = (participationId: number, currentFilePath: string, newFilename: string) => of();
    update = (participationId: number, fileName: string, fileContent: string) => of();
}
