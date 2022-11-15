import { NgbTimeStringAdapter } from 'app/course/tutorial-groups/shared/ngbTimeStringAdapter';

describe('NgbTimeStringAdapter', () => {
    const adapter = new NgbTimeStringAdapter();

    describe('fromModel', () => {
        it('should split hh:mm:ss correctly', () => {
            const input = '12:34:56';
            const result = adapter.fromModel(input);
            expect(result).toBeTruthy();
            expect(result!.hour).toBe(12);
            expect(result!.minute).toBe(34);
            expect(result!.second).toBe(56);
        });

        it('should handle single digits', () => {
            const input = '01:02:03';
            const result = adapter.fromModel(input);
            expect(result).toBeTruthy();
            expect(result!.hour).toBe(1);
            expect(result!.minute).toBe(2);
            expect(result!.second).toBe(3);
        });

        it('should handle null', () => {
            expect(adapter.fromModel(null)).toBeNull();
        });
    });

    describe('toModel', () => {
        it('should pad single digits so that hh:mm:ss is created', () => {
            const model = { hour: 1, minute: 2, second: 3 };
            const result = adapter.toModel(model);
            expect(result).toBe('01:02:03');
        });

        it('should convert model to hh:mm:ss', () => {
            const model = { hour: 12, minute: 34, second: 56 };
            const result = adapter.toModel(model);
            expect(result).toBe('12:34:56');
        });

        it('should handle null', () => {
            expect(adapter.toModel(null)).toBeNull();
        });
    });
});
