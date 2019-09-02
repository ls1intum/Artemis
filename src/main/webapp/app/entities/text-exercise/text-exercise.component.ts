import { Component, Input } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';

import { TextExercise } from './text-exercise.model';
import { TextExerciseService } from './text-exercise.service';
import { CourseExerciseService, CourseService } from '../course';
import { ActivatedRoute } from '@angular/router';
import { ExerciseComponent } from 'app/entities/exercise/exercise.component';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { AccountService } from 'app/core';
import { DeleteDialogComponent } from 'app/delete-dialog/delete-dialog.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-text-exercise',
    templateUrl: './text-exercise.component.html',
})
export class TextExerciseComponent extends ExerciseComponent {
    @Input() textExercises: TextExercise[];

    constructor(
        private textExerciseService: TextExerciseService,
        private courseExerciseService: CourseExerciseService,
        courseService: CourseService,
        translateService: TranslateService,
        private jhiAlertService: JhiAlertService,
        eventManager: JhiEventManager,
        route: ActivatedRoute,
        private accountService: AccountService,
        private artemisMarkdown: ArtemisMarkdown,
        private modalService: NgbModal,
    ) {
        super(courseService, translateService, route, eventManager);
        this.textExercises = [];
    }

    protected loadExercises(): void {
        this.courseExerciseService.findAllTextExercisesForCourse(this.courseId).subscribe(
            (res: HttpResponse<TextExercise[]>) => {
                this.textExercises = res.body!;

                // reconnect exercise with course
                this.textExercises.forEach(exercise => {
                    exercise.course = this.course;
                    exercise.isAtLeastTutor = this.accountService.isAtLeastTutorInCourse(exercise.course);
                    exercise.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(exercise.course);
                });
                this.emitExerciseCount(this.textExercises.length);
            },
            (res: HttpErrorResponse) => this.onError(res),
        );
    }

    trackId(index: number, item: TextExercise) {
        return item.id;
    }

    /**
     * Opens delete text exercise popup
     * @param exerciseId the id of exercise
     */
    openDeleteTextExercisePopup(exerciseId: number) {
        const textExercise = this.textExercises.find(exercise => exercise.id === exerciseId);
        if (!textExercise) {
            return;
        }
        const modalRef = this.modalService.open(DeleteDialogComponent, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.entityTitle = textExercise.title;
        modalRef.componentInstance.deleteQuestion = this.translateService.instant('artemisApp.textExercise.delete.question', { title: textExercise.title });
        modalRef.componentInstance.deleteConfirmationText = 'Please type in the name of the Exercise to confirm.';
        modalRef.result.then(
            result => {
                this.textExerciseService.delete(exerciseId).subscribe(response => {
                    this.eventManager.broadcast({
                        name: 'textExerciseListModification',
                        content: 'Deleted an textExercise',
                    });
                });
            },
            reason => {},
        );
    }

    protected getChangeEventName(): string {
        return 'textExerciseListModification';
    }

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message);
    }

    callback() {}
}
