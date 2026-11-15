import { TumAetUiButtonDirective, TumAetUiTableDirective, TumAetUiTableLayoutComponent, TumAetUiTableSortEvent, TumAetUiTableSortableColumnComponent } from '@tumaet/ui-angular';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faXmark } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { Component, booleanAttribute, computed, input, signal } from '@angular/core';
import { orderBy } from 'lodash-es';
import { ProfilePictureComponent } from 'app/shared-ui/profile-picture/profile-picture.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { addPublicFilePrefix } from 'app/app.constants';
import { TutorialGroupStudent } from 'app/openapi/model/tutorial-group-student';
import { cloneWith } from 'app/foundation/util/deep-clone.util';

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
        const field = this.sortField() as keyof TutorialGroupStudent;
        const key = (student: TutorialGroupStudent) => String(student[field] ?? '').toLowerCase();
        return orderBy(this.studentsWithCompleteProfilePictureUrl(), [key], [this.sortOrder() === 1 ? 'asc' : 'desc']);
    }
}
