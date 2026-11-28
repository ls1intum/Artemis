import { TumAetUiButtonDirective } from '@tumaet/ui-angular/button';
import { TumAetUiTableDirective, TumAetUiTableSortEvent, TumAetUiTableSortableColumnComponent } from '@tumaet/ui-angular/table-directive';
import { TumAetUiTableLayoutComponent } from '@tumaet/ui-angular/table-layout';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faXmark } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { Component, booleanAttribute, computed, input, signal } from '@angular/core';
import { orderBy } from 'lodash-es';
import dayjs from 'dayjs/esm';
import { ProfilePictureComponent } from 'app/shared-ui/profile-picture/profile-picture.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { addPublicFilePrefix } from 'app/app.constants';
import { TutorialGroupStudent } from 'app/openapi/model/tutorial-group-student';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';

export interface TutorialRegistrationsStudentsTableRemoveActionColumnInfo {
    headerStringKey: string;
    onRemove: (event: Event, student: TutorialGroupStudent) => void;
}

@Component({
    selector: 'jhi-tutorial-registrations-students-table',
    imports: [
        TumAetUiTableDirective,
        TumAetUiTableLayoutComponent,
        TumAetUiTableSortableColumnComponent,
        ProfilePictureComponent,
        TranslateDirective,
        TumAetUiButtonDirective,
        FaIconComponent,
        ArtemisTranslatePipe,
        ArtemisDatePipe,
    ],
    templateUrl: './tutorial-registrations-students-table.component.html',
})
export class TutorialRegistrationsStudentsTableComponent {
    protected readonly faXmark = faXmark;

    students = input.required<TutorialGroupStudent[]>();
    removeActionColumnInfo = input<TutorialRegistrationsStudentsTableRemoveActionColumnInfo>();
    /** Set when the consumer projects controls with the attribute `studentsTableToolbar`. */
    hasToolbar = input(false, { transform: booleanAttribute });
    /** Translation key of the empty state heading, for example "no results" while a search is active. Defaults to "No entries yet". */
    emptyTitle = input<string>();
    /** Shows when each student was registered. Only the participant list sets it; selected but not yet registered students have no date. */
    showRegistrationDate = input(false, { transform: booleanAttribute });
    sortField = signal<string>('name');
    sortOrder = signal<number>(1);
    studentsWithCompleteProfilePictureUrl = computed(() => this.computeStudentsWithCompleteProfilePictureUrl());
    sortedStudents = computed(() => this.computeSortedStudents());

    onSort(event: TumAetUiTableSortEvent): void {
        this.sortField.set(event.field);
        this.sortOrder.set(event.order);
    }

    private computeStudentsWithCompleteProfilePictureUrl(): TutorialGroupStudent[] {
        return this.students().map((student) => cloneWith(student, { profilePictureUrl: addPublicFilePrefix(student.profilePictureUrl) }));
    }

    private computeSortedStudents(): TutorialGroupStudent[] {
        const order = this.sortOrder() === 1 ? 'asc' : 'desc';
        if (this.sortField() === 'registrationDate') {
            // Registrations from before the date was recorded have none; they stay last in both directions instead of sorting as the empty string
            const hasNoDate = (student: TutorialGroupStudent) => !student.registrationDate;
            // Compare parsed instants: serialized ISO strings differ in their number of fractional-second digits
            const date = (student: TutorialGroupStudent) => (student.registrationDate ? dayjs(student.registrationDate).valueOf() : 0);
            return orderBy(this.studentsWithCompleteProfilePictureUrl(), [hasNoDate, date], ['asc', order]);
        }
        const field = this.sortField() as keyof TutorialGroupStudent;
        const key = (student: TutorialGroupStudent) => String(student[field] ?? '').toLowerCase();
        return orderBy(this.studentsWithCompleteProfilePictureUrl(), [key], [order]);
    }
}
