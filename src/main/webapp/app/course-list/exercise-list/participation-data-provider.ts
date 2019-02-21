import { Injectable } from '@angular/core';
import { Participation } from '../../entities/participation';

@Injectable({ providedIn: 'root' })
export class ParticipationDataProvider {
    public participationStorage: Participation;

    public constructor() {}
}
