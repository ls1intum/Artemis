import { FormControl, FormGroup } from '@angular/forms';
import { validTimeRange } from 'app/course/tutorial-groups/shared/timeRangeValidator';

describe('TimeRangeValidator', () => {
    it('should mark time range as invalid if start time is after end time', () => {
        const formGroup = new FormGroup({
            startTime: new FormControl('10:00:00'),
            endTime: new FormControl('09:00:00'),
        });
        const result = validTimeRange(formGroup);
        expect(result).toEqual({ invalidTimeRange: true });
    });
    it('should return null if start time is before end time', () => {
        const formGroup = new FormGroup({
            startTime: new FormControl('09:00:00'),
            endTime: new FormControl('10:00:00'),
        });
        const result = validTimeRange(formGroup);
        expect(result).toBeNull();
    });
    it('should return null if start time is not set', () => {
        const formGroup = new FormGroup({
            startTime: new FormControl('09:00:00'),
            endTime: new FormControl(undefined),
        });
        const result = validTimeRange(formGroup);
        expect(result).toBeNull();
    });
    it('should return null if end time is not set', () => {
        const formGroup = new FormGroup({
            startTime: new FormControl(undefined),
            endTime: new FormControl('10:00:00'),
        });
        const result = validTimeRange(formGroup);
        expect(result).toBeNull();
    });
});
