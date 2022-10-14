import { Component, Input, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AlertService } from 'app/core/util/alert.service';
import { LectureService } from './lecture.service';
import { CourseManagementService } from '../course/manage/course-management.service';
import { Lecture } from 'app/entities/lecture.model';
import { EditorMode } from 'app/shared/markdown-editor/markdown-editor.component';
import { KatexCommand } from 'app/shared/markdown-editor/commands/katex.command';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { faSave, faHandshakeAngle, faArrowRight } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-lecture-update-wizard',
    templateUrl: './lecture-update-wizard.component.html',
    styleUrls: ['./lecture-update-wizard.component.scss'],
})
export class LectureUpdateWizardComponent implements OnInit {
    @Input() toggleModeFunction: () => void;
    @Input() saveLectureFunction: () => void;
    @Input() lecture: Lecture;
    @Input() isSaving: boolean;
    @Input() startDate: string;
    @Input() endDate: string;

    currentStep: number;
    isAddingLearningGoal: boolean;

    domainCommandsDescription = [new KatexCommand()];
    EditorMode = EditorMode;

    // Icons
    faSave = faSave;
    faHandShakeAngle = faHandshakeAngle;
    faArrowRight = faArrowRight;

    constructor(
        protected alertService: AlertService,
        protected lectureService: LectureService,
        protected courseService: CourseManagementService,
        protected activatedRoute: ActivatedRoute,
        private navigationUtilService: ArtemisNavigationUtilService,
        private router: Router,
    ) {}

    /**
     * Life cycle hook called by Angular to indicate that Angular is done creating the component
     */
    ngOnInit() {
        this.isSaving = false;
        this.currentStep = this.lecture.startDate !== undefined || this.lecture.endDate !== undefined ? 2 : 1;
    }

    /**
     * Progress to the next step of the wizard mode
     */
    next() {
        if (this.currentStep === 2) {
            this.saveLectureFunction();
            return;
        }

        this.currentStep++;
    }

    onLectureCreationSucceeded() {
        this.currentStep++;
    }

    /**
     * Checks if the given step has already been completed
     */
    isCompleted(step: number) {
        return this.currentStep > step;
    }

    /**
     * Checks if the given step is the current one
     */
    isCurrent(step: number) {
        return this.currentStep === step;
    }

    getNextIcon() {
        return this.currentStep < 5 ? faArrowRight : faSave;
    }

    getNextText() {
        return this.currentStep < 5 ? 'artemisApp.lecture.home.nextStepLabel' : 'entity.action.save';
    }

    toggleWizardMode() {
        if (this.currentStep <= 2) {
            this.toggleModeFunction();
        } else {
            this.router.navigate(['course-management', this.lecture.course!.id, 'lectures', this.lecture.id]);
        }
    }
}
