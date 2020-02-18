import { of, Observable } from 'rxjs';
import { IRepositoryFileService } from 'app/entities/repository/repository.service';

export class MockRepositoryFileService implements IRepositoryFileService {
    createFile = (participationId: number, fileName: string) => Observable.empty();
    createFolder = (participationId: number, folderName: string) => Observable.empty();
    delete = (participationId: number, fileName: string) => Observable.empty();
    get = (participationId: number, fileName: string) => of();
    query = (participationId: number) => of({});
    rename = (participationId: number, currentFilePath: string, newFilename: string) => Observable.empty();
    update = (participationId: number, fileName: string, fileContent: string) => of();
}
