import dayjs from 'dayjs/esm';

export class MockArtemisServerDateService {
    now(): dayjs.Dayjs {
        return dayjs();
    }
}
