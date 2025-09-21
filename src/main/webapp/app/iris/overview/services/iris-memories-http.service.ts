import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { MemirisMemory, MemirisMemoryWithRelationsDTO } from 'app/iris/shared/entities/memiris.model';

/**
 * Lightweight HTTP service for Memiris memories (current user scope).
 */
@Injectable({ providedIn: 'root' })
export class IrisMemoriesHttpService {
    private readonly http = inject(HttpClient);
    private readonly apiPrefix = 'api/iris/memories/user';

    /**
     * Lists all memories for the current user.
     */
    listUserMemories(): Observable<MemirisMemory[]> {
        return this.http.get<MemirisMemory[]>(`${this.apiPrefix}`);
    }

    /**
     * Gets a specific memory (with relations) for the current user.
     */
    getUserMemory(memoryId: string): Observable<MemirisMemoryWithRelationsDTO> {
        return this.http.get<MemirisMemoryWithRelationsDTO>(`${this.apiPrefix}/${encodeURIComponent(memoryId)}`);
    }

    /**
     * Deletes a specific memory for the current user.
     */
    deleteUserMemory(memoryId: string): Observable<void> {
        return this.http.delete<void>(`${this.apiPrefix}/${encodeURIComponent(memoryId)}`);
    }
}
