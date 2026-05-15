import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';

import { BlockDefinitionModel } from '../../shared/entities/block-definition.model';

@Injectable({ providedIn: 'root' })
export class ProofBlockRegistryService {
    private http = inject(HttpClient);

    private readonly _blocks = signal<BlockDefinitionModel[]>([]);

    /** Read-only signal of all loaded block descriptors, consumed by pipes and components. */
    readonly blocks = this._blocks.asReadonly();

    /** Fetches the block registry and caches the result in the signal. */
    getBlockRegistry(): Observable<BlockDefinitionModel[]> {
        return this.http.get<BlockDefinitionModel[]>('api/proof/block-registry').pipe(tap((result) => this._blocks.set(result)));
    }

    /** Synchronous descriptor lookup for use in pipes and computed properties. */
    descriptorFor(type: string): BlockDefinitionModel | undefined {
        return this._blocks().find((b) => b.type === type);
    }
}
