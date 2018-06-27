import { Component, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import ApollonEditor, { layoutDiagram } from '@ls1intum/apollon';
import { generateDragAndDropQuizExercise } from './quiz-exercise-generator';
import { Course, CourseService } from '../../entities/course';
import { QuizExerciseService } from '../../entities/quiz-exercise';
import { FileUploaderService } from '../../shared/http/file-uploader.service';

@Component({
    selector: 'jhi-apollon-quiz-exercise-generation',
    templateUrl: './apollon-quiz-exercise-generation.component.html',
    providers: []
})
export class ApollonQuizExerciseGenerationComponent implements OnInit {
    apollonEditor: ApollonEditor;
    courses: Course[];
    selectedCourse: Course;

    constructor(
        private activeModal: NgbActiveModal,
        private courseService: CourseService,
        private fileUploaderService: FileUploaderService,
        private quizExerciseService: QuizExerciseService
    ) {}

    ngOnInit() {
        this.courseService.findAll().subscribe(
            courses => {
                this.courses = courses;
                this.selectedCourse = courses[0];
            },
            () => {
                // TODO: handle error
            }
        );
    }

    async save() {
        if (this.selectedCourse === undefined) {
            return;
        }

        const diagramState = this.apollonEditor.getState();
        const layoutedDiagram = layoutDiagram(diagramState, { outerPadding: 50 });
        const interactiveElementIds = new Set(diagramState.interactiveElements.allIds);

        const fontFamily = '-apple-system, BlinkMacSystemFont, "Segoe UI", Arial, sans-serif';

        await generateDragAndDropQuizExercise(
            layoutedDiagram,
            interactiveElementIds,
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
