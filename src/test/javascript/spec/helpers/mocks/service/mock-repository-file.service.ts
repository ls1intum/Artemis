import { of, empty } from 'rxjs';
import { IRepositoryFileService } from 'app/exercises/shared/result/repository.service';

export class MockRepositoryFileService implements IRepositoryFileService {
    createFile = (participationId: number, fileName: string) => empty();
    createFolder = (participationId: number, folderName: string) => empty();
    delete = (participationId: number, fileName: string) => empty();
    get = (participationId: number, fileName: string) => of();
    query = (participationId: number) => of({});
    rename = (participationId: number, currentFilePath: string, newFilename: string) => empty();
    update = (participationId: number, fileName: string, fileContent: string) => of();
}
