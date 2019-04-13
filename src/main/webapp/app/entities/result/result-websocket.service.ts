import { Injectable } from '@angular/core';
import * as moment from 'moment';

import { Result } from './result.model';
import { JhiWebsocketService, AccountService } from 'app/core';

@Injectable({ providedIn: 'root' })
export class ResultWebsocketService {
    constructor(private jhiWebsocketService: JhiWebsocketService, private accountService: AccountService) {}

    async subscribeResultForParticipation(participationId: number, callback: (result: Result) => void): Promise<() => void> {
        return this.accountService
            .identity()
            .then(() => {
                const channel = `/topic/participation/${participationId}/newResults`;
                this.jhiWebsocketService.subscribe(channel);
                this.jhiWebsocketService.receive(channel).subscribe((newResult: Result) => {
                    console.log('Received new result ' + newResult.id + ': ' + newResult.resultString);
                    // convert json string to moment
                    newResult.completionDate = newResult.completionDate != null ? moment(newResult.completionDate) : null;
                    callback(newResult);
                });
                return channel;
            })
            .then(channel => () => this.jhiWebsocketService.unsubscribe(channel));
    }
}
