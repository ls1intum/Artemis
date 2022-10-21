import { IRepositoryFileService } from 'app/exercises/shared/result/repository.service';
import { EMPTY, of } from 'rxjs';

export class MockRepositoryFileService implements IRepositoryFileService {
    createFile = (participationId: number, fileName: string) => EMPTY;
    createFolder = (participationId: number, folderName: string) => EMPTY;
    delete = (participationId: number, fileName: string) => EMPTY;
    get = (participationId: number, fileName: string) => of();
    query = (participationId: number) => of({});
    rename = (participationId: number, currentFilePath: string, newFilename: string) => EMPTY;
    update = (participationId: number, fileName: string, fileContent: string) => of();
}
