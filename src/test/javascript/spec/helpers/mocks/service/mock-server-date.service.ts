import { ServerDateService } from 'app/shared/server-date.service';
import dayjs from 'dayjs/esm';
import { HttpClient } from '@angular/common/http';

export class MockArtemisServerDateService implements ServerDateService {
    recentClientDates: Array<dayjs.Dayjs>;
    recentOffsets: Array<number>;
    resourceUrl: string;
    http: HttpClient;

    now(): dayjs.Dayjs {
        return dayjs();
    }

    setServerDate(date: string): void {}

    updateTime(): void {}
}
