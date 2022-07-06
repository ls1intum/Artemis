import { NgbDateDayjsAdapter } from 'app/core/config/datepicker-adapter';
import dayjs from 'dayjs/esm';

describe('NgbDateDayjsAdapter', () => {
    let adapter: NgbDateDayjsAdapter;

    beforeEach(() => (adapter = new NgbDateDayjsAdapter()));

    it('should convert valid dates to struct', () => {
        const date = dayjs('2022-02-20');
        const result = adapter.fromModel(date);

        expect(result).toEqual({ year: 2022, month: 2, day: 20 });
    });

    it('should return null for null inputs', () => {
        expect(adapter.fromModel(null)).toBe(null);
    });

    it('should return null for other objects that are not dayjs object', () => {
        expect(adapter.fromModel({} as dayjs.Dayjs)).toBe(null);
    });

    it('should return null for invalid dates', () => {
        const date = dayjs('foobar');
        const result = adapter.fromModel(date);
        expect(result).toBe(null);
    });

    it('should convert date structs to dayjs correctly', () => {
        const input = { year: 2022, month: 1, day: 15 };
        const result = adapter.toModel(input);
        expect(result).toEqual(dayjs('2022-01-15'));
    });

    it('should return null from toModel if null is passed in', () => {
        expect(adapter.toModel(null)).toBe(null);
    });
});
