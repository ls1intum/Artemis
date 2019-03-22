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
    providers: []
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
        private quizExerciseService: QuizExerciseService
    ) {}

    ngOnInit() {
        this.courseService.query().subscribe(
            response => {
                this.courses = response.body;
                this.selectedCourse = this.courses[0];
            }, () => { }
        );
    }

    async save() {
        if (this.selectedCourse === undefined) {
            return;
        }

        const model = this.apollonEditor.model;
        const layoutedDiagram = layoutDiagram(model, { outerPadding: 20 });
        const interactiveElements = new Set(model.interactive.elements);
        const interactiveRelationships = new Set(model.interactive.relationships);

        const fontFamily = '-apple-system, BlinkMacSystemFont, "Segoe UI", Arial, sans-serif';

        await generateDragAndDropQuizExercise(
            this.diagramTitle,
            layoutedDiagram,
            interactiveElements,
            interactiveRelationships,
            fontFamily,
            this.selectedCourse,
            this.fileUploaderService,
            this.quizExerciseService
        );

        this.activeModal.close();
    }

    dismiss() {
        this.activeModal.dismiss('cancel');
    }
}
