import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { BlockDefinitionModel } from '../../shared/entities/block-definition.model';

@Injectable({ providedIn: 'root' })
export class ProofBlockRegistryService {
    private http = inject(HttpClient);

    getBlockRegistry(): Observable<BlockDefinitionModel[]> {
        return this.http.get<BlockDefinitionModel[]>('api/proof/block-registry');
    }
}
