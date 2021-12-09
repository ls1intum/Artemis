import { Component, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import { ApollonEditor } from '@ls1intum/apollon';
import { generateDragAndDropQuizExercise } from 'app/exercises/quiz/manage/apollon-diagrams/exercise-generation/quiz-exercise-generator';
import { Course } from 'app/entities/course.model';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { faBan, faSave } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-apollon-quiz-exercise-generation',
    templateUrl: './apollon-quiz-exercise-generation.component.html',
    providers: [],
})
export class ApollonQuizExerciseGenerationComponent implements OnInit {
    apollonEditor: ApollonEditor;
    diagramTitle: string;
    course: Course;
    courseTitle: string;
    courseId: number;

    // Icons
    faSave = faSave;
    faBan = faBan;

    constructor(
        private activeModal: NgbActiveModal,
        private courseService: CourseManagementService,
        private fileUploaderService: FileUploaderService,
        private quizExerciseService: QuizExerciseService,
    ) {}

    /**
     * Initializes courses from the server and assigns selected course
     */
    ngOnInit() {
        this.courseService.find(this.getCourseId()).subscribe((response) => {
            this.course = response.body!;
            this.courseTitle = this.course.title!;
        });
    }

    /**
     * Parses the courseId.
     */
    getCourseId() {
        // tslint:disable-next-line:radix
        return parseInt(location.toString().split('management/')[1].split('/')[0]);
    }

    /**
     * Generates quiz exercise from Apollon diagram model
     */
    async save() {
        if (this.course === undefined) {
            return;
        }

        const model = this.apollonEditor.model;

        try {
            const quizExercise = await generateDragAndDropQuizExercise(this.course, this.diagramTitle, model, this.fileUploaderService, this.quizExerciseService);
            this.activeModal.close(quizExercise);
        } catch (error) {
            this.activeModal.dismiss(error);
        }
    }

    /**
     * Closes the dialog
     */
    dismiss() {
        this.activeModal.close();
    }
}
