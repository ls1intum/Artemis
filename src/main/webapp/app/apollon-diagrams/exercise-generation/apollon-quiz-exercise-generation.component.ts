import { Component, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { generateDragAndDropQuizExercise } from './quiz-exercise-generator';
import { Course, CourseService } from '../../entities/course';
import { QuizExerciseService } from '../../entities/quiz-exercise';
import { FileUploaderService } from '../../shared/http/file-uploader.service';
import { ApollonEditor } from '@ls1intum/apollon';

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

    ngOnInit() {
        this.courseService.query().subscribe(response => {
            this.courses = response.body;
            this.selectedCourse = this.courses[0];
        });
    }

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

    dismiss() {
        this.activeModal.close();
    }
}
