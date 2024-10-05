import { Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Subject } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';
import { ApollonDiagramCreateFormComponent } from 'app/exercises/quiz/manage/apollon-diagrams/apollon-diagram-create-form.component';
import { ApollonDiagramService } from 'app/exercises/quiz/manage/apollon-diagrams/apollon-diagram.service';
import { ApollonDiagram } from 'app/entities/apollon-diagram.model';
import { SortService } from 'app/shared/service/sort.service';
import { AccountService } from 'app/core/auth/account.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { faPlus, faSort, faTimes, faX } from '@fortawesome/free-solid-svg-icons';
import { ButtonSize } from 'app/shared/components/button.component';
import { UMLDiagramType } from '@ls1intum/apollon';

@Component({
    selector: 'jhi-apollon-diagram-list',
    templateUrl: './apollon-diagram-list.component.html',
    providers: [ApollonDiagramService],
})
export class ApollonDiagramListComponent implements OnInit {
    private apollonDiagramsService = inject(ApollonDiagramService);
    private alertService = inject(AlertService);
    private modalService = inject(NgbModal);
    private sortService = inject(SortService);
    private route = inject(ActivatedRoute);
    private courseService = inject(CourseManagementService);
    private accountService = inject(AccountService);

    apollonDiagrams: ApollonDiagram[] = [];
    predicate: string;
    reverse: boolean;
    @Input()
    courseId: number;

    @Output() openDiagram = new EventEmitter<number>();
    @Output() closeDialog = new EventEmitter();

    course: Course;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    // Icons
    faSort = faSort;
    faPlus = faPlus;
    faX = faX;
    faTimes = faTimes;

    ButtonSize = ButtonSize;

    constructor() {
        this.predicate = 'id';
        this.reverse = true;
    }

    /**
     * Initializes Apollon diagrams from the server
     */
    ngOnInit() {
        this.courseId ??= Number(this.route.snapshot.paramMap.get('courseId'));

        this.courseService.find(this.courseId).subscribe((courseResponse: HttpResponse<Course>) => {
            this.course = courseResponse.body!;
        });
        this.loadDiagrams();
    }

    /**
     * Loads the Apollon diagrams of this course which will be shown
     */
    loadDiagrams() {
        this.apollonDiagramsService.getDiagramsByCourse(this.courseId).subscribe({
            next: (response) => {
                this.apollonDiagrams = response.body!;
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
        this.apollonDiagramsService.delete(apollonDiagram.id!, this.courseId).subscribe({
            next: () => {
                this.alertService.success('artemisApp.apollonDiagram.delete.success', { title: apollonDiagram.title });
                this.apollonDiagrams = this.apollonDiagrams.filter((diagram) => {
                    return diagram.id !== apollonDiagram.id;
                });
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
     * @param index of a diagram in the collection
     * @param item current diagram
     */
    trackId(index: number, item: ApollonDiagram) {
        return item.id;
    }

    sortRows() {
        this.sortService.sortByProperty(this.apollonDiagrams, this.predicate, this.reverse);
    }
}
