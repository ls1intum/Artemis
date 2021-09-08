import { ServerDateService } from 'app/shared/server-date.service';
import moment from 'moment';
import { HttpClient } from '@angular/common/http';

export class MockArtemisServerDateService implements ServerDateService {
    recentClientDates: Array<moment.Moment>;
    recentOffsets: Array<number>;
    resourceUrl: string;
    http: HttpClient;

    now(): moment.Moment {
        return moment();
    }

    setServerDate(date: string): void {}

    updateTime(): void {}
}
