import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { DialogService } from 'primeng/dynamicdialog';
import { Subject } from 'rxjs';
import { AlertService } from 'app/foundation/service/alert.service';
import { ApollonDiagramCreateFormComponent } from 'app/quiz/manage/apollon-diagrams/create-form/apollon-diagram-create-form.component';
import { ApollonDiagramService } from 'app/quiz/manage/apollon-diagrams/services/apollon-diagram.service';
import { ApollonDiagram } from 'app/modeling/shared/entities/apollon-diagram.model';
import { SortService } from 'app/foundation/service/sort.service';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { Course } from 'app/course/shared/entities/course.model';
import { faDiagramProject, faPlus, faTrash, faXmark } from '@fortawesome/free-solid-svg-icons';
import { UMLDiagramType } from '@tumaet/apollon';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { TumUiButtonDirective, TumUiTableDirective, TumUiTableSortEvent, TumUiTableSortableColumnComponent } from '@tumaet/ui-angular';

@Component({
    selector: 'jhi-apollon-diagram-list',
    templateUrl: './apollon-diagram-list.component.html',
    styleUrls: ['./apollon-diagram-list.component.scss'],
    providers: [ApollonDiagramService],
    imports: [TranslateDirective, FaIconComponent, ArtemisTranslatePipe, DeleteButtonDirective, TumUiButtonDirective, TumUiTableDirective, TumUiTableSortableColumnComponent],
})
export class ApollonDiagramListComponent {
    private apollonDiagramsService = inject(ApollonDiagramService);
    private alertService = inject(AlertService);
    private dialogService = inject(DialogService);
    private sortService = inject(SortService);
    private route = inject(ActivatedRoute);
    private courseService = inject(CourseManagementService);

    apollonDiagrams = signal<ApollonDiagram[]>([]);
    readonly predicate = signal('id');
    readonly ascending = signal(true);

    courseId = input<number>();

    internalCourseId = computed(() => this.courseId() ?? Number(this.route.snapshot.paramMap.get('courseId')));

    openDiagram = output<number>();
    closeDialog = output<void>();

    course = signal<Course | null>(null);

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    // Icons
    faPlus = faPlus;
    faXmark = faXmark;
    faTrash = faTrash;
    faDiagramProject = faDiagramProject;

    constructor() {
        effect(() => {
            const id = this.internalCourseId();
            if (id) {
                this.courseService.find(id).subscribe((courseResponse: HttpResponse<Course>) => {
                    this.course.set(courseResponse.body);
                });
                this.loadDiagrams();
            }
        });
    }

    /**
     * Loads the Apollon diagrams of this course which will be shown
     */
    loadDiagrams() {
        this.apollonDiagramsService.getDiagramsByCourse(this.internalCourseId()).subscribe({
            next: (response) => {
                // Sorted on arrival so the rows match the sort indicator the header already shows.
                this.apollonDiagrams.set(this.sorted(response.body!));
            },
            error: () => {
                this.alertService.error('artemisApp.apollonDiagram.home.error.loading');
            },
        });
    }

    /**
     * Deletes specified Apollon diagram
     * @param apollonDiagram
     */
    delete(apollonDiagram: ApollonDiagram) {
        this.apollonDiagramsService.delete(apollonDiagram.id!, this.internalCourseId()).subscribe({
            next: () => {
                this.alertService.success('artemisApp.apollonDiagram.delete.success', { title: apollonDiagram.title });
                this.apollonDiagrams.update((diagrams) => diagrams.filter((diagram) => diagram.id !== apollonDiagram.id));
                this.dialogErrorSource.next('');
            },
            error: () => {
                this.alertService.error('artemisApp.apollonDiagram.delete.error', { title: apollonDiagram.title });
            },
        });
    }

    /**
     * Returns the title for Apollon diagram
     * @param diagram
     */
    getTitleForApollonDiagram(diagram: ApollonDiagram): string {
        return diagram.title && diagram.title.trim().length ? diagram.title.trim() : `#${diagram.id}`;
    }

    /**
     * Opens dialog for creating a new diagram
     */
    openCreateDiagramDialog(courseId: number) {
        const ref = this.dialogService.open(ApollonDiagramCreateFormComponent, {
            width: '50rem',
            breakpoints: {
                '850px': '95vw',
            },
            modal: true,
            closable: true,
            closeOnEscape: true,
            dismissableMask: false,
            draggable: false,
            resizable: false,
            showHeader: false,
            contentStyle: { padding: '0' },
            // class diagram is the default value and can be changed by the user in the creation dialog
            data: { apollonDiagram: new ApollonDiagram(UMLDiagramType.ClassDiagram, courseId) },
        });
        ref?.onClose.subscribe((diagram: ApollonDiagram | undefined) => {
            if (diagram) {
                this.handleOpenDialogClick(diagram.id!);
            }
        });
    }

    handleOpenDialogClick(apollonDiagramId: number) {
        this.openDiagram.emit(apollonDiagramId);
    }

    handleCloseDiagramClick() {
        this.closeDialog.emit();
    }

    sortRows(event: TumUiTableSortEvent) {
        this.predicate.set(event.field);
        this.ascending.set(event.order >= 0);
        this.apollonDiagrams.set(this.sorted(this.apollonDiagrams()));
    }

    /** Returns a new array so the signal notifies; sortByProperty sorts in place. */
    private sorted(diagrams: ApollonDiagram[]): ApollonDiagram[] {
        const copy = [...diagrams];
        this.sortService.sortByProperty(copy, this.predicate(), this.ascending());
        return copy;
    }
}
