import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * Request DTO for code generation
 */
export type CodeGenerationRequestDTO = object;

/**
 * Response DTO for code generation
 */
export interface CodeGenerationResultDTO {
    success: boolean;
    message: string;
    attempts: number;
}

/**
 * Service for interacting with Hyperion code generation functionality
 */
@Injectable({ providedIn: 'root' })
export class CodeGenerationService {
    private http = inject(HttpClient);

    private resourceUrl = 'api/hyperion';

    /**
     * Generates code for a programming exercise using AI
     * @param exerciseId the ID of the programming exercise
     * @param request the request options (currently empty)
     * @returns Observable of the generation result
     */
    generateCode(exerciseId: number, request: CodeGenerationRequestDTO = {}): Observable<CodeGenerationResultDTO> {
        return this.http.post<CodeGenerationResultDTO>(`${this.resourceUrl}/exercises/${exerciseId}/generate-code`, request);
    }
}
