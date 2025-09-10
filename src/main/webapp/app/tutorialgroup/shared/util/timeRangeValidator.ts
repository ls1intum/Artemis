import { AbstractControl, ValidationErrors } from '@angular/forms';
import dayjs from 'dayjs/esm';

export const validTimeRange = (control: AbstractControl): ValidationErrors | null => {
    if (!control.get('startTime')!.value || !control.get('endTime')!.value) {
        return null;
    }

    const startTime = control.get('startTime')!.value;
    const endTime = control.get('endTime')!.value;

    const startComparison = dayjs('1970-01-01 ' + startTime, 'YYYY-MM-DD HH:mm:ss');
    const endComparison = dayjs('1970-01-01 ' + endTime, 'YYYY-MM-DD HH:mm:ss');
    if (startComparison.isAfter(endComparison)) {
        return {
            invalidTimeRange: true,
        };
    } else {
        return null;
    }
};
