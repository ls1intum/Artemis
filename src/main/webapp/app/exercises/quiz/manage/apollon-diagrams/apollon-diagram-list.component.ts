import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/core/alert/alert.service';
import { ApollonDiagramCreateFormComponent } from 'app/exercises/quiz/manage/apollon-diagrams/apollon-diagram-create-form.component';
import { ApollonDiagramService } from 'app/exercises/quiz/manage/apollon-diagrams/apollon-diagram.service';
import { ApollonDiagram } from 'app/entities/apollon-diagram.model';
import { UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { AccountService } from 'app/core/auth/account.service';

@Component({
    selector: 'jhi-apollon-diagram-list',
    templateUrl: './apollon-diagram-list.component.html',
    providers: [ApollonDiagramService],
})
export class ApollonDiagramListComponent implements OnInit {
    apollonDiagrams: ApollonDiagram[] = [];
    predicate: string;
    reverse: boolean;
    courseId: number;
    course: Course;

    constructor(
        private apollonDiagramsService: ApollonDiagramService,
        private courseService: CourseManagementService,
        private accountService: AccountService,
        private jhiAlertService: AlertService,
        private modalService: NgbModal,
        private route: ActivatedRoute,
        private router: Router,
    ) {
        this.predicate = 'id';
        this.reverse = true;
    }

    /**
     * Initializes Apollon diagrams from the server
     */
    ngOnInit() {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.courseService.find(this.courseId).subscribe(
            (courseResponse) => {
                this.course = courseResponse.body!;
                this.course.isAtLeastTutor = this.accountService.isAtLeastTutorInCourse(this.course);
                this.course.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(this.course);

                this.load();
            }
        );
    }

    load() {
        if (this.course.isAtLeastTutor || this.course.isAtLeastInstructor) {
            this.apollonDiagramsService.getDiagramsByCourse(this.courseId).subscribe(
                (response) => {
                    this.apollonDiagrams = response.body!;
                },
                () => {
                    this.jhiAlertService.error('artemisApp.apollonDiagram.home.error.loading');
                },
            );
        }
    }

    /**
     * Deletes specified Apollon diagram
     * @param apollonDiagram
     */
    delete(apollonDiagram: ApollonDiagram) {
        this.apollonDiagramsService.delete(apollonDiagram.id).subscribe(
            () => {
                this.jhiAlertService.success('artemisApp.apollonDiagram.delete.success', { title: apollonDiagram.title });
                this.apollonDiagrams = this.apollonDiagrams.filter((diagram) => {
                    return diagram.id !== apollonDiagram.id;
                });
            },
            () => {
                this.jhiAlertService.error('artemisApp.apollonDiagram.delete.error', { title: apollonDiagram.title });
            },
        );
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
    }

    /**
     * Returns the unique identifier for items in the collection
     * @param index of a diagram in the collection
     * @param item current diagram
     */
    trackId(index: number, item: ApollonDiagram) {
        return item.id;
    }

    /**
     * Used in the template for jhiSort
     */
    callback() {}

    /**
     * Revert to the previous state, equivalent with pressing the back button on your browser
     */
    previousState() {
        window.history.back();
    }
}
