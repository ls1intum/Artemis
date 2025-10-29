import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Subject } from 'rxjs';
import { AlertService } from 'app/shared/service/alert.service';
import { ApollonDiagramCreateFormComponent } from 'app/quiz/manage/apollon-diagrams/create-form/apollon-diagram-create-form.component';
import { ApollonDiagramService } from 'app/quiz/manage/apollon-diagrams/services/apollon-diagram.service';
import { ApollonDiagram } from 'app/modeling/shared/entities/apollon-diagram.model';
import { SortService } from 'app/shared/service/sort.service';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { faPlus, faSort, faTimes, faX } from '@fortawesome/free-solid-svg-icons';
import { ButtonSize } from 'app/shared/components/buttons/button/button.component';
import { UMLDiagramType } from '@tumaet/apollon';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { SortByDirective } from 'app/shared/sort/directive/sort-by.directive';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';

@Component({
    selector: 'jhi-apollon-diagram-list',
    templateUrl: './apollon-diagram-list.component.html',
    providers: [ApollonDiagramService],
    imports: [TranslateDirective, FaIconComponent, SortDirective, SortByDirective, DeleteButtonDirective],
})
export class ApollonDiagramListComponent {
    private apollonDiagramsService = inject(ApollonDiagramService);
    private alertService = inject(AlertService);
    private modalService = inject(NgbModal);
    private sortService = inject(SortService);
    private route = inject(ActivatedRoute);
    private courseService = inject(CourseManagementService);

    apollonDiagrams = signal<ApollonDiagram[]>([]);
    predicate = 'id';
    reverse = true;

    courseId = input<number>();

    internalCourseId = computed(() => this.courseId() ?? Number(this.route.snapshot.paramMap.get('courseId')));

    openDiagram = output<number>();
    closeDialog = output<void>();

    course = signal<Course | null>(null);

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    // Icons
    faSort = faSort;
    faPlus = faPlus;
    faX = faX;
    faTimes = faTimes;

    ButtonSize = ButtonSize;

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
                this.apollonDiagrams.set(response.body!);
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
        const modalRef = this.modalService.open(ApollonDiagramCreateFormComponent, { size: 'lg', backdrop: 'static' });
        const formComponentInstance = modalRef.componentInstance as ApollonDiagramCreateFormComponent;
        // class diagram is the default value and can be changed by the user in the creation dialog
        formComponentInstance.apollonDiagram = new ApollonDiagram(UMLDiagramType.ClassDiagram, courseId);
        modalRef.result.then((diagram) => this.handleOpenDialogClick(diagram.id));
    }

    handleOpenDialogClick(apollonDiagramId: number) {
        this.openDiagram.emit(apollonDiagramId);
    }

    handleCloseDiagramClick() {
        this.closeDialog.emit();
    }

    /**
     * Returns the unique identifier for items in the collection
     * @param _index of a diagram in the collection
     * @param item current diagram
     */
    trackId(_index: number, item: ApollonDiagram) {
        return item.id;
    }

    sortRows() {
        const sorted = [...this.apollonDiagrams()];
        this.sortService.sortByProperty(sorted, this.predicate, this.reverse);
        this.apollonDiagrams.set(sorted);
    }
}
