import { EventEmitter, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

/**
 * This service is used to pass cached repository files between parent and child components where we cannot rely on Angular event binding.
 */
@Injectable({
    providedIn: 'root',
})
export class CachedRepositoryFilesService {
    cachedRepositoryFilesChanged = new EventEmitter<Map<string, Map<string, string>>>();

    getCachedRepositoryFilesObservable(): Observable<Map<string, Map<string, string>>> {
        return this.cachedRepositoryFilesChanged.asObservable();
    }

    emitCachedRepositoryFiles(data: Map<string, Map<string, string>>) {
        this.cachedRepositoryFilesChanged.emit(data);
    }
}
