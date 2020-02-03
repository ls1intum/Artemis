import { Component, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Course } from 'app/entities/course';
import { CourseService } from 'app/entities/course/course.service';
import { QuizExerciseService } from 'app/entities/quiz-exercise';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import { ApollonEditor } from '@ls1intum/apollon';
import { generateDragAndDropQuizExercise } from 'app/apollon-diagrams/exercise-generation/quiz-exercise-generator';

@Component({
    selector: 'jhi-apollon-quiz-exercise-generation',
    templateUrl: './apollon-quiz-exercise-generation.component.html',
    providers: [],
})
export class ApollonQuizExerciseGenerationComponent implements OnInit {
    apollonEditor: ApollonEditor;
    diagramTitle: string;
    courses: Course[];
    selectedCourse: Course;

    constructor(
        private activeModal: NgbActiveModal,
        private courseService: CourseService,
        private fileUploaderService: FileUploaderService,
        private quizExerciseService: QuizExerciseService,
    ) {}

    /**
     * Initializes courses from the server and assigns selected course
     */
    ngOnInit() {
        this.courseService.query().subscribe(response => {
            this.courses = response.body!;
            this.selectedCourse = this.courses[0];
        });
    }

    /**
     * Generates quiz exercise from Apollon diagram model
     */
    async save() {
        if (this.selectedCourse === undefined) {
            return;
        }

        const model = this.apollonEditor.model;

        try {
            const quizExercise = await generateDragAndDropQuizExercise(this.selectedCourse, this.diagramTitle, model, this.fileUploaderService, this.quizExerciseService);
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
