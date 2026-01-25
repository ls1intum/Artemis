import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { MemirisMemoryDataDTO, MemirisMemoryWithRelationsDTO } from 'app/iris/shared/entities/memiris.model';

/**
 * Lightweight HTTP service for Memiris memories (current user scope).
 */
@Injectable({ providedIn: 'root' })
export class IrisMemoriesHttpService {
    private readonly http = inject(HttpClient);
    private readonly apiPrefix = 'api/iris/user';

    /**
     * Loads aggregated memory data for the current user.
     * Returns the flat memories along with learnings and connections in a single payload.
     */
    getUserMemoryData(): Observable<MemirisMemoryDataDTO> {
        return this.http.get<MemirisMemoryDataDTO>(`${this.apiPrefix}/memoryData`);
    }

    /**
     * Retrieves a specific memory (with relations) for the current user.
     * The returned DTO includes learnings and connections associated with the memory.
     */
    getUserMemory(memoryId: string): Observable<MemirisMemoryWithRelationsDTO> {
        return this.http.get<MemirisMemoryWithRelationsDTO>(`${this.apiPrefix}/memory/${encodeURIComponent(memoryId)}`);
    }

    /**
     * Deletes a specific memory for the current user.
     * The operation is idempotent; deleting a missing memory results in a server-side 404.
     */
    deleteUserMemory(memoryId: string): Observable<void> {
        return this.http.delete<void>(`${this.apiPrefix}/memory/${encodeURIComponent(memoryId)}`);
    }
}
