import { Component, computed, inject, input, model, viewChild } from '@angular/core';
import { Observable, of } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, map, switchMap } from 'rxjs/operators';
import { faUserMinus } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTypeahead } from '@ng-bootstrap/ng-bootstrap';
import { TumAetUiButtonComponent, TumAetUiInputDirective } from '@tumaet/ui-angular';

import { User } from 'app/account/user/user.model';
import { addPublicFilePrefix } from 'app/app.constants';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ActionType } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { ProfilePictureComponent } from 'app/shared-ui/profile-picture/profile-picture.component';
import { CellTemplateRef, ColumnDef, TableViewComponent, TableViewOptions } from 'app/shared-ui/table-view/table-view';

@Component({
    selector: 'jhi-presentation-assessment-presenter-selector',
    templateUrl: './presentation-assessment-presenter-selector.component.html',
    imports: [
        ArtemisTranslatePipe,
        DeleteButtonDirective,
        FaIconComponent,
        NgbTypeahead,
        ProfilePictureComponent,
        TableViewComponent,
        TranslateDirective,
        TumAetUiButtonComponent,
        TumAetUiInputDirective,
    ],
})
export class PresentationAssessmentPresenterSelectorComponent {
    private readonly courseManagementService = inject(CourseManagementService);

    readonly courseId = input.required<number>();
    readonly presenters = model<User[]>([]);
    readonly editable = input(true);

    protected readonly ActionType = ActionType;
    protected readonly faUserMinus = faUserMinus;
    protected readonly addPublicFilePrefix = addPublicFilePrefix;

    readonly profilePictureTemplate = viewChild<CellTemplateRef<User>>('profilePictureTemplate');
    readonly columns = computed<ColumnDef<User>[]>(() => [
        {
            headerKey: 'artemisApp.presentationAssessment.picture',
            width: '5rem',
            templateRef: this.profilePictureTemplate(),
        },
        {
            field: 'login',
            headerKey: 'artemisApp.course.courseGroup.login',
            sort: true,
        },
        {
            field: 'name',
            headerKey: 'artemisApp.course.courseGroup.name',
            sort: true,
        },
    ]);
    readonly tableOptions: TableViewOptions = {
        lazy: false,
        paginated: false,
        showCurrentPageReport: false,
        showSearch: false,
        initialSortField: 'name',
    };

    readonly searchPresenters = (searchTerms: Observable<string>): Observable<User[]> =>
        searchTerms.pipe(
            debounceTime(200),
            distinctUntilChanged(),
            switchMap((term) => {
                if (term.length < 3) {
                    return of([]);
                }
                return this.courseManagementService.searchStudents(this.courseId(), term, 0, 25).pipe(
                    map((response) => {
                        const assignedIds = new Set(this.presenters().map((presenter) => presenter.id));
                        return (response.body ?? []).filter((user) => !assignedIds.has(user.id));
                    }),
                    catchError(() => of([])),
                );
            }),
        );

    readonly presenterFormatter = (presenter: User): string => `${presenter.name ?? ''} (${presenter.login ?? ''})`;

    selectPresenter(presenter: User): void {
        const alreadyAssigned = this.presenters().some((assignedPresenter) =>
            presenter.id !== undefined ? assignedPresenter.id === presenter.id : assignedPresenter.login === presenter.login,
        );
        if (!alreadyAssigned) {
            this.presenters.update((current) => [...current, presenter]);
        }
    }

    removePresenter(presenter: User): void {
        this.presenters.update((current) => current.filter((candidate) => (presenter.id !== undefined ? candidate.id !== presenter.id : candidate.login !== presenter.login)));
    }
}
