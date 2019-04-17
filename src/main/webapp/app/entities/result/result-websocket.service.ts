import { Injectable } from '@angular/core';
import * as moment from 'moment';

import { Result } from './result.model';
import { JhiWebsocketService, AccountService } from 'app/core';
import { Subject, Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ResultWebsocketService {
    private subscriptions: { [key: string]: Subject<Result> } = {};

    constructor(private jhiWebsocketService: JhiWebsocketService, private accountService: AccountService) {}

    async subscribeResultForParticipation(participationId: number): Promise<Observable<Result>> {
        return this.accountService.identity().then(() => {
            const channel = `/topic/participation/${participationId}/newResults`;
            if (!this.subscriptions[channel]) {
                this.subscriptions[channel] = new Subject();
                this.jhiWebsocketService.subscribe(channel);
                this.jhiWebsocketService.receive(channel).subscribe((newResult: Result) => {
                    console.log('Received new result ' + newResult.id + ': ' + newResult.resultString);
                    // convert json string to moment
                    newResult.completionDate = newResult.completionDate != null ? moment(newResult.completionDate) : null;
                    this.subscriptions[channel].next(newResult);
                });
            }
            return this.subscriptions[channel];
        });
    }
}
