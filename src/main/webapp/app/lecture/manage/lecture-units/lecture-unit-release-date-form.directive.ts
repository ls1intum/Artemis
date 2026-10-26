import { Directive, signal } from '@angular/core';
import { FormGroup } from '@angular/forms';
import dayjs from 'dayjs/esm';

/** Release-date input handling shared by lecture forms with autosave. */
@Directive()
export abstract class LectureUnitReleaseDateFormDirective {
    abstract form: FormGroup;
    readonly isReleaseDateTextValid = signal(true);
    protected abstract reportChange(immediate: boolean): void;

    get releaseDateControl() {
        return this.form.get('releaseDate');
    }

    onReleaseDateChange(releaseDate: dayjs.Dayjs | undefined): void {
        this.releaseDateControl?.setValue(releaseDate);
        this.releaseDateControl?.markAsDirty();
        this.reportChange(true);
    }

    onReleaseDateTextValidityChange(valid: boolean): void {
        // The picker also reports the validity it starts with, which is no change of the user.
        if (valid === this.isReleaseDateTextValid()) {
            return;
        }
        this.isReleaseDateTextValid.set(valid);
        this.reportChange(false);
    }
}
