import { HttpErrorResponse } from '@angular/common/http';
import { Component, HostListener, Input, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Patch, Selection, UMLDiagramType, UMLElementType, UMLModel, UMLRelationshipType } from '@ls1intum/apollon';
import { TranslateService } from '@ngx-translate/core';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { ComplaintType } from 'app/entities/complaint.model';
import { Feedback, buildFeedbackTextForReview, checkSubsequentFeedbackInAssessment } from 'app/entities/feedback.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Result } from 'app/entities/result.model';
import { getFirstResultWithComplaint, getLatestSubmissionResult } from 'app/entities/submission.model';
import { ModelingAssessmentService } from 'app/exercises/modeling/assess/modeling-assessment.service';
import { ModelingSubmissionService } from 'app/exercises/modeling/participate/modeling-submission.service';
import { ModelingEditorComponent } from 'app/exercises/modeling/shared/modeling-editor.component';
import { hasExerciseDueDatePassed } from 'app/exercises/shared/exercise/exercise.utils';
import { addParticipationToResult, getUnreferencedFeedback } from 'app/exercises/shared/result/result.utils';
import { AccountService } from 'app/core/auth/account.service';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { modelingTour } from 'app/guided-tour/tours/modeling-tour';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { ButtonType } from 'app/shared/components/button.component';
import { AUTOSAVE_CHECK_INTERVAL, AUTOSAVE_EXERCISE_INTERVAL, AUTOSAVE_TEAM_EXERCISE_INTERVAL } from 'app/shared/constants/exercise-exam-constants';
import { ComponentCanDeactivate } from 'app/shared/guard/can-deactivate.model';
import { stringifyIgnoringFields } from 'app/shared/util/utils';
import { Subject, Subscription, TeardownLogic } from 'rxjs';
import { omit } from 'lodash-es';
import dayjs from 'dayjs/esm';
import { AlertService } from 'app/core/util/alert.service';
import { getCourseFromExercise } from 'app/entities/exercise.model';
import { Course } from 'app/entities/course.model';
import { getNamesForAssessments } from '../assess/modeling-assessment.util';
import { faExclamationTriangle, faGripLines } from '@fortawesome/free-solid-svg-icons';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { onError } from 'app/shared/util/global.utils';
import { SubmissionPatch } from 'app/entities/submission-patch.model';

@Component({
    selector: 'jhi-modeling-submission',
    templateUrl: './modeling-submission.component.html',
    styleUrls: ['./modeling-submission.component.scss'],
})
export class ModelingSubmissionComponent implements OnInit, OnDestroy, ComponentCanDeactivate {
    readonly addParticipationToResult = addParticipationToResult;
    readonly buildFeedbackTextForReview = buildFeedbackTextForReview;

    @ViewChild(ModelingEditorComponent, { static: false })
    modelingEditor: ModelingEditorComponent;
    ButtonType = ButtonType;

    @Input() participationId?: number;
    @Input() displayHeader: boolean = true;
    @Input() isPrinting?: boolean = false;
    @Input() expandProblemStatement?: boolean = false;

    @Input() inputExercise?: ModelingExercise;
    @Input() inputSubmission?: ModelingSubmission;
    @Input() inputParticipation?: StudentParticipation;

    private subscription: Subscription;
    private resultUpdateListener: Subscription;

    participation: StudentParticipation;
    isOwnerOfParticipation: boolean;

    modelingExercise: ModelingExercise;
    course?: Course;
    result?: Result;
    resultWithComplaint?: Result;

    selectedEntities: string[];
    selectedRelationships: string[];

    submission: ModelingSubmission;

    assessmentResult?: Result;
    assessmentsNames: Map<string, Map<string, string>>;
    totalScore: number;

    umlModel: UMLModel; // input model for Apollon
    hasElements = false; // indicates if the current model has at least one element
    isSaving: boolean;
    isChanged: boolean;
    retryStarted = false;
    autoSaveInterval: number;
    autoSaveTimer: number;

    explanation: string; // current explanation on text editor

    automaticSubmissionWebsocketChannel: string;

    // indicates if the assessment due date is in the past. the assessment will not be loaded and displayed to the student if it is not.
    isAfterAssessmentDueDate: boolean;
    isLoading: boolean;
    isLate: boolean; // indicates if the submission is late
    ComplaintType = ComplaintType;
    examMode = false;

    // submission sync with team members
    private submissionChange = new Subject<ModelingSubmission>();
    submissionObservable = this.submissionChange.asObservable();
    submissionPatchObservable = new Subject<SubmissionPatch>();

    // private modelingEditorInitialized = new ReplaySubject<void>();
    resizeOptions = { verticalResize: true };

    // Icons
    faGripLines = faGripLines;
    farListAlt = faListAlt;
    faExclamationTriangle = faExclamationTriangle;

    constructor(
        private jhiWebsocketService: JhiWebsocketService,
        private modelingSubmissionService: ModelingSubmissionService,
        private modelingAssessmentService: ModelingAssessmentService,
        private alertService: AlertService,
        private route: ActivatedRoute,
        private translateService: TranslateService,
        private participationWebsocketService: ParticipationWebsocketService,
        private guidedTourService: GuidedTourService,
        private accountService: AccountService,
    ) {
        this.isSaving = false;
        this.autoSaveTimer = 0;
        this.isLoading = true;
    }

    ngOnInit(): void {
        if (this.inputValuesArePresent()) {
            this.setupComponentWithInputValues();
        } else {
            this.subscription = this.route.params.subscribe((params) => {
                const participationId = params['participationId'] ?? this.participationId;

                if (participationId) {
                    this.modelingSubmissionService.getLatestSubmissionForModelingEditor(participationId).subscribe({
                        next: (modelingSubmission) => {
                            this.updateModelingSubmission(modelingSubmission);
                            if (this.modelingExercise.teamMode) {
                                this.setupSubmissionStreamForTeam();
                            } else {
                                this.setAutoSaveTimer();
                            }
                        },
                        error: (error: HttpErrorResponse) => onError(this.alertService, error),
                    });
                }
            });
        }

        const isDisplayedOnExamSummaryPage = !this.displayHeader && this.participationId !== undefined;
        if (!isDisplayedOnExamSummaryPage) {
            window.scroll(0, 0);
        }
    }

    private inputValuesArePresent(): boolean {
        return !!(this.inputExercise || this.inputSubmission || this.inputParticipation);
    }

    /**
     * Uses values directly passed to this component instead of subscribing to a participation to save resources
     *
     * <i>e.g. used within {@link ExamResultSummaryComponent} and the respective {@link ModelingExamSummaryComponent}
     * as directly after the exam no grading is present and only the student solution shall be displayed </i>
     * @private
     */
    private setupComponentWithInputValues() {
        if (this.inputExercise) {
            this.modelingExercise = this.inputExercise;
        }
        if (this.inputSubmission) {
            this.submission = this.inputSubmission;
        }
        if (this.inputParticipation) {
            this.participation = this.inputParticipation;
        }

        if (this.submission.model) {
            this.umlModel = JSON.parse(this.submission.model);
            this.hasElements = this.umlModel.elements && Object.values(this.umlModel.elements).length !== 0;
        }

        this.explanation = this.submission.explanationText ?? '';
    }

    /**
     * Updates the modeling submission with the given modeling submission.
     */
    private updateModelingSubmission(modelingSubmission: ModelingSubmission) {
        if (!modelingSubmission) {
            this.alertService.error('artemisApp.apollonDiagram.submission.noSubmission');
        }

        this.submission = modelingSubmission;

        // reconnect participation <--> result
        if (getLatestSubmissionResult(modelingSubmission)) {
            modelingSubmission.participation!.results = [getLatestSubmissionResult(modelingSubmission)!];
        }
        this.participation = modelingSubmission.participation as StudentParticipation;
        this.isOwnerOfParticipation = this.accountService.isOwnerOfParticipation(this.participation);

        // reconnect participation <--> submission
        this.participation.submissions = [<ModelingSubmission>omit(modelingSubmission, 'participation')];

        this.modelingExercise = this.participation.exercise as ModelingExercise;
        this.course = getCourseFromExercise(this.modelingExercise);
        this.modelingExercise.studentParticipations = [this.participation];
        this.examMode = !!this.modelingExercise.exerciseGroup;
        if (this.modelingExercise.diagramType == undefined) {
            this.modelingExercise.diagramType = UMLDiagramType.ClassDiagram;
        }
        // checks if the student started the exercise after the due date
        this.isLate =
            this.modelingExercise &&
            !!this.modelingExercise.dueDate &&
            !!this.participation.initializationDate &&
            dayjs(this.participation.initializationDate).isAfter(this.modelingExercise.dueDate);
        this.isAfterAssessmentDueDate = !this.modelingExercise.assessmentDueDate || dayjs().isAfter(this.modelingExercise.assessmentDueDate);
        if (this.submission.model) {
            this.umlModel = JSON.parse(this.submission.model);
            this.hasElements = this.umlModel.elements && Object.values(this.umlModel.elements).length !== 0;
        }

        const patchModel =
            '{"version":"3.0.0","type":"BPMN","size":{"width":1500,"height":1380},"interactive":{"elements":{},"relationships":{}},"elements":{"d918cf35-213d-474c-afcb-15d1232df80a":{"id":"d918cf35-213d-474c-afcb-15d1232df80a","name":"Loan Applicant","type":"BPMNPool","owner":null,"bounds":{"x":-730,"y":-670,"width":1450,"height":200}},"da1ef25c-72a1-4876-9dc2-fd3b216d2a1d":{"id":"da1ef25c-72a1-4876-9dc2-fd3b216d2a1d","name":"","type":"BPMNStartEvent","owner":"d918cf35-213d-474c-afcb-15d1232df80a","bounds":{"x":-660,"y":-590,"width":40,"height":40},"eventType":"default"},"75d3878b-910c-4f39-857a-86d26bfc3eef":{"id":"75d3878b-910c-4f39-857a-86d26bfc3eef","name":"Send credit request","type":"BPMNTask","owner":"d918cf35-213d-474c-afcb-15d1232df80a","bounds":{"x":-580,"y":-600,"width":180,"height":60},"taskType":"default","marker":"none"},"5742df27-c2fa-439b-b48f-d7e959ebdcca":{"id":"5742df27-c2fa-439b-b48f-d7e959ebdcca","name":"","type":"BPMNIntermediateEvent","owner":"d918cf35-213d-474c-afcb-15d1232df80a","bounds":{"x":360,"y":-590,"width":40,"height":40},"eventType":"message-catch"},"2dc1e552-70f9-4713-874a-9ebcbad0ec76":{"id":"2dc1e552-70f9-4713-874a-9ebcbad0ec76","name":"Review quote","type":"BPMNTask","owner":"d918cf35-213d-474c-afcb-15d1232df80a","bounds":{"x":440,"y":-600,"width":160,"height":60},"taskType":"default","marker":"none"},"64832ccc-577a-4810-88ff-01c76cb75953":{"id":"64832ccc-577a-4810-88ff-01c76cb75953","name":"","type":"BPMNEndEvent","owner":"d918cf35-213d-474c-afcb-15d1232df80a","bounds":{"x":640,"y":-590,"width":40,"height":40},"eventType":"default"},"c7ff7d06-bad2-40aa-9f19-88d034553301":{"id":"c7ff7d06-bad2-40aa-9f19-88d034553301","name":"Credit Institute","type":"BPMNPool","owner":null,"bounds":{"x":-730,"y":-420,"width":1450,"height":460}},"45a20d74-92d2-4e83-930e-9b2343dccd97":{"id":"45a20d74-92d2-4e83-930e-9b2343dccd97","name":"Loan Assessor","type":"BPMNSwimlane","owner":"c7ff7d06-bad2-40aa-9f19-88d034553301","bounds":{"x":-690,"y":-130,"width":1410,"height":170}},"b31edaf0-6205-4ed2-9827-68e35b275774":{"id":"b31edaf0-6205-4ed2-9827-68e35b275774","name":"Assess risk","type":"BPMNTask","owner":"45a20d74-92d2-4e83-930e-9b2343dccd97","bounds":{"x":-220,"y":-70,"width":160,"height":60},"taskType":"default","marker":"none"},"731181cd-f7a1-495a-8512-414d09aa4b7b":{"id":"731181cd-f7a1-495a-8512-414d09aa4b7b","name":"Loan Processor","type":"BPMNSwimlane","owner":"c7ff7d06-bad2-40aa-9f19-88d034553301","bounds":{"x":-690,"y":-420,"width":1410,"height":290}},"5dc1af21-c8d0-4b45-920a-db2c4998a77e":{"id":"5dc1af21-c8d0-4b45-920a-db2c4998a77e","name":"Credit request received","type":"BPMNStartEvent","owner":"731181cd-f7a1-495a-8512-414d09aa4b7b","bounds":{"x":-590,"y":-350,"width":40,"height":40},"eventType":"message"},"86fe72e2-f8f3-4d19-84ec-5e0b221d705d":{"id":"86fe72e2-f8f3-4d19-84ec-5e0b221d705d","name":"Review request","type":"BPMNTask","owner":"731181cd-f7a1-495a-8512-414d09aa4b7b","bounds":{"x":-510,"y":-360,"width":160,"height":60},"taskType":"default","marker":"none"},"0f3564eb-1fba-4e6c-b038-2fbf2eb3bda1":{"id":"0f3564eb-1fba-4e6c-b038-2fbf2eb3bda1","name":"","type":"BPMNGateway","owner":"731181cd-f7a1-495a-8512-414d09aa4b7b","bounds":{"x":-310,"y":-350,"width":40,"height":40},"gatewayType":"parallel"},"561a9090-1840-4aea-ac63-03d42c25d5df":{"id":"561a9090-1840-4aea-ac63-03d42c25d5df","name":"Standard terms applicable?","type":"BPMNGateway","owner":"731181cd-f7a1-495a-8512-414d09aa4b7b","bounds":{"x":-240,"y":-350,"width":40,"height":40},"gatewayType":"exclusive"},"ee5ae07d-fad0-4860-b7d4-cb63b361e9ca":{"id":"ee5ae07d-fad0-4860-b7d4-cb63b361e9ca","name":"Calculate terms","type":"BPMNTask","owner":"731181cd-f7a1-495a-8512-414d09aa4b7b","bounds":{"x":-160,"y":-360,"width":150,"height":60},"taskType":"default","marker":"none"},"acf00ac7-164a-4d24-a265-9f7ec21166e4":{"id":"acf00ac7-164a-4d24-a265-9f7ec21166e4","name":"","type":"BPMNGateway","owner":"731181cd-f7a1-495a-8512-414d09aa4b7b","bounds":{"x":30,"y":-350,"width":40,"height":40},"gatewayType":"exclusive"},"9e77e27f-768a-44ae-9ee8-687fc1c9e64d":{"id":"9e77e27f-768a-44ae-9ee8-687fc1c9e64d","name":"Prepare contract","type":"BPMNTask","owner":"731181cd-f7a1-495a-8512-414d09aa4b7b","bounds":{"x":110,"y":-360,"width":160,"height":60},"taskType":"default","marker":"none"},"67896fe5-7831-4175-840d-f493aa0933b2":{"id":"67896fe5-7831-4175-840d-f493aa0933b2","name":"","type":"BPMNGateway","owner":"731181cd-f7a1-495a-8512-414d09aa4b7b","bounds":{"x":320,"y":-350,"width":40,"height":40},"gatewayType":"parallel"},"4bcb89cb-1d3a-45c3-a495-976a7ede3696":{"id":"4bcb89cb-1d3a-45c3-a495-976a7ede3696","name":"Send quote","type":"BPMNTask","owner":"731181cd-f7a1-495a-8512-414d09aa4b7b","bounds":{"x":400,"y":-360,"width":160,"height":60},"taskType":"default","marker":"none"},"a27def38-dcd8-4d61-9ad3-dd3e217e267b":{"id":"a27def38-dcd8-4d61-9ad3-dd3e217e267b","name":"Prepare special terms","type":"BPMNTask","owner":"731181cd-f7a1-495a-8512-414d09aa4b7b","bounds":{"x":-160,"y":-240,"width":150,"height":60},"taskType":"default","marker":"none"},"ccf93a16-a718-4304-8a47-f3da022eb603":{"id":"ccf93a16-a718-4304-8a47-f3da022eb603","name":"","type":"BPMNEndEvent","owner":null,"bounds":{"x":610,"y":-350,"width":40,"height":40},"eventType":"default"}},"relationships":{"25989ff9-6610-4288-8426-a83612ee9133":{"id":"25989ff9-6610-4288-8426-a83612ee9133","name":"","type":"BPMNFlow","owner":null,"bounds":{"x":-620,"y":-570,"width":40,"height":1},"path":[{"x":0,"y":0},{"x":40,"y":0}],"source":{"direction":"Right","element":"da1ef25c-72a1-4876-9dc2-fd3b216d2a1d"},"target":{"direction":"Left","element":"75d3878b-910c-4f39-857a-86d26bfc3eef"},"isManuallyLayouted":false,"flowType":"sequence"},"bc65bd2c-6b86-47f3-9b59-2d44a536ee8e":{"id":"bc65bd2c-6b86-47f3-9b59-2d44a536ee8e","name":"","type":"BPMNFlow","owner":null,"bounds":{"x":-570,"y":-540,"width":80,"height":190},"path":[{"x":80,"y":0},{"x":80,"y":95},{"x":0,"y":95},{"x":0,"y":190}],"source":{"direction":"Down","element":"75d3878b-910c-4f39-857a-86d26bfc3eef"},"target":{"direction":"Up","element":"5dc1af21-c8d0-4b45-920a-db2c4998a77e"},"isManuallyLayouted":false,"flowType":"message"},"e1f37e6b-5ae9-4947-9a8a-db01e356c22a":{"id":"e1f37e6b-5ae9-4947-9a8a-db01e356c22a","name":"","type":"BPMNFlow","owner":null,"bounds":{"x":-400,"y":-570,"width":760,"height":1},"path":[{"x":0,"y":0},{"x":760,"y":0}],"source":{"direction":"Right","element":"75d3878b-910c-4f39-857a-86d26bfc3eef"},"target":{"direction":"Left","element":"5742df27-c2fa-439b-b48f-d7e959ebdcca"},"isManuallyLayouted":false,"flowType":"sequence"},"b89c3a36-2988-4878-804c-df88670c1110":{"id":"b89c3a36-2988-4878-804c-df88670c1110","name":"","type":"BPMNFlow","owner":null,"bounds":{"x":400,"y":-570,"width":40,"height":1},"path":[{"x":0,"y":0},{"x":40,"y":0}],"source":{"direction":"Right","element":"5742df27-c2fa-439b-b48f-d7e959ebdcca"},"target":{"direction":"Left","element":"2dc1e552-70f9-4713-874a-9ebcbad0ec76"},"isManuallyLayouted":false,"flowType":"sequence"},"895fe9a5-f2e0-40b1-b74b-ca132e0d2a87":{"id":"895fe9a5-f2e0-40b1-b74b-ca132e0d2a87","name":"","type":"BPMNFlow","owner":null,"bounds":{"x":600,"y":-570,"width":40,"height":1},"path":[{"x":0,"y":0},{"x":40,"y":0}],"source":{"direction":"Right","element":"2dc1e552-70f9-4713-874a-9ebcbad0ec76"},"target":{"direction":"Left","element":"64832ccc-577a-4810-88ff-01c76cb75953"},"isManuallyLayouted":false,"flowType":"sequence"},"437a2dce-9b3d-40a3-b378-31431e484fc5":{"id":"437a2dce-9b3d-40a3-b378-31431e484fc5","name":"","type":"BPMNFlow","owner":null,"bounds":{"x":-550,"y":-330,"width":40,"height":1},"path":[{"x":0,"y":0},{"x":40,"y":0}],"source":{"direction":"Right","element":"5dc1af21-c8d0-4b45-920a-db2c4998a77e"},"target":{"direction":"Left","element":"86fe72e2-f8f3-4d19-84ec-5e0b221d705d"},"isManuallyLayouted":false,"flowType":"sequence"},"fe601c9b-517b-4b31-8401-12df0be09c61":{"id":"fe601c9b-517b-4b31-8401-12df0be09c61","name":"","type":"BPMNFlow","owner":null,"bounds":{"x":-350,"y":-330,"width":40,"height":1},"path":[{"x":0,"y":0},{"x":40,"y":0}],"source":{"direction":"Right","element":"86fe72e2-f8f3-4d19-84ec-5e0b221d705d"},"target":{"direction":"Left","element":"0f3564eb-1fba-4e6c-b038-2fbf2eb3bda1"},"isManuallyLayouted":false,"flowType":"sequence"},"7121cf1a-972c-42ba-9040-1e52956b281b":{"id":"7121cf1a-972c-42ba-9040-1e52956b281b","name":"","type":"BPMNFlow","owner":null,"bounds":{"x":-270,"y":-330,"width":30,"height":1},"path":[{"x":0,"y":0},{"x":30,"y":0}],"source":{"direction":"Right","element":"0f3564eb-1fba-4e6c-b038-2fbf2eb3bda1"},"target":{"direction":"Left","element":"561a9090-1840-4aea-ac63-03d42c25d5df"},"isManuallyLayouted":false,"flowType":"sequence"},"8f6f8ff1-badb-4068-a966-5f1345a2fa48":{"id":"8f6f8ff1-badb-4068-a966-5f1345a2fa48","name":"yes","type":"BPMNFlow","owner":null,"bounds":{"x":-200,"y":-370,"width":40,"height":41},"path":[{"x":0,"y":40},{"x":40,"y":40}],"source":{"direction":"Right","element":"561a9090-1840-4aea-ac63-03d42c25d5df"},"target":{"direction":"Left","element":"ee5ae07d-fad0-4860-b7d4-cb63b361e9ca"},"isManuallyLayouted":false,"flowType":"sequence"},"d3fc6c3c-1f75-4247-923a-5d185238775e":{"id":"d3fc6c3c-1f75-4247-923a-5d185238775e","name":"","type":"BPMNFlow","owner":null,"bounds":{"x":-290,"y":-310,"width":70,"height":270},"path":[{"x":0,"y":0},{"x":0,"y":270},{"x":70,"y":270}],"source":{"direction":"Down","element":"0f3564eb-1fba-4e6c-b038-2fbf2eb3bda1"},"target":{"direction":"Left","element":"b31edaf0-6205-4ed2-9827-68e35b275774"},"isManuallyLayouted":false,"flowType":"sequence"},"02e69539-824b-4f11-b814-dc604c455945":{"id":"02e69539-824b-4f11-b814-dc604c455945","name":"no","type":"BPMNFlow","owner":null,"bounds":{"x":-220,"y":-310,"width":60,"height":100},"path":[{"x":0,"y":0},{"x":0,"y":100},{"x":60,"y":100}],"source":{"direction":"Down","element":"561a9090-1840-4aea-ac63-03d42c25d5df"},"target":{"direction":"Left","element":"a27def38-dcd8-4d61-9ad3-dd3e217e267b"},"isManuallyLayouted":false,"flowType":"sequence"},"e64682d3-65b1-4367-89d2-abd72da140ae":{"id":"e64682d3-65b1-4367-89d2-abd72da140ae","name":"","type":"BPMNFlow","owner":null,"bounds":{"x":-10,"y":-330,"width":40,"height":1},"path":[{"x":0,"y":0},{"x":40,"y":0}],"source":{"direction":"Right","element":"ee5ae07d-fad0-4860-b7d4-cb63b361e9ca"},"target":{"direction":"Left","element":"acf00ac7-164a-4d24-a265-9f7ec21166e4"},"isManuallyLayouted":false,"flowType":"sequence"},"e9370403-7c40-4664-89ce-56720b5f16cd":{"id":"e9370403-7c40-4664-89ce-56720b5f16cd","name":"","type":"BPMNFlow","owner":null,"bounds":{"x":-10,"y":-310,"width":60,"height":100},"path":[{"x":0,"y":100},{"x":60,"y":100},{"x":60,"y":0}],"source":{"direction":"Right","element":"a27def38-dcd8-4d61-9ad3-dd3e217e267b"},"target":{"direction":"Down","element":"acf00ac7-164a-4d24-a265-9f7ec21166e4"},"isManuallyLayouted":false,"flowType":"sequence"},"641e6ba0-8fde-4e8a-9a36-b2dcad0bf021":{"id":"641e6ba0-8fde-4e8a-9a36-b2dcad0bf021","name":"","type":"BPMNFlow","owner":null,"bounds":{"x":70,"y":-330,"width":40,"height":1},"path":[{"x":0,"y":0},{"x":40,"y":0}],"source":{"direction":"Right","element":"acf00ac7-164a-4d24-a265-9f7ec21166e4"},"target":{"direction":"Left","element":"9e77e27f-768a-44ae-9ee8-687fc1c9e64d"},"isManuallyLayouted":false,"flowType":"sequence"},"9569e58a-f4bc-4e42-a2e3-9bb04d8e736f":{"id":"9569e58a-f4bc-4e42-a2e3-9bb04d8e736f","name":"","type":"BPMNFlow","owner":null,"bounds":{"x":270,"y":-330,"width":50,"height":1},"path":[{"x":0,"y":0},{"x":50,"y":0}],"source":{"direction":"Right","element":"9e77e27f-768a-44ae-9ee8-687fc1c9e64d"},"target":{"direction":"Left","element":"67896fe5-7831-4175-840d-f493aa0933b2"},"isManuallyLayouted":false,"flowType":"sequence"},"bf3706d4-b7a6-463b-bd27-b4835de46b48":{"id":"bf3706d4-b7a6-463b-bd27-b4835de46b48","name":"","type":"BPMNFlow","owner":null,"bounds":{"x":-60,"y":-310,"width":400,"height":270},"path":[{"x":0,"y":270},{"x":400,"y":270},{"x":400,"y":0}],"source":{"direction":"Right","element":"b31edaf0-6205-4ed2-9827-68e35b275774"},"target":{"direction":"Down","element":"67896fe5-7831-4175-840d-f493aa0933b2"},"isManuallyLayouted":false,"flowType":"sequence"},"d4254d33-a1bb-4a3d-80d4-597de9af4d97":{"id":"d4254d33-a1bb-4a3d-80d4-597de9af4d97","name":"","type":"BPMNFlow","owner":null,"bounds":{"x":360,"y":-330,"width":40,"height":1},"path":[{"x":0,"y":0},{"x":40,"y":0}],"source":{"direction":"Right","element":"67896fe5-7831-4175-840d-f493aa0933b2"},"target":{"direction":"Left","element":"4bcb89cb-1d3a-45c3-a495-976a7ede3696"},"isManuallyLayouted":false,"flowType":"sequence"},"821a4592-1736-4db2-ac18-428700d62730":{"id":"821a4592-1736-4db2-ac18-428700d62730","name":"","type":"BPMNFlow","owner":null,"bounds":{"x":560,"y":-330,"width":50,"height":1},"path":[{"x":0,"y":0},{"x":50,"y":0}],"source":{"direction":"Right","element":"4bcb89cb-1d3a-45c3-a495-976a7ede3696"},"target":{"direction":"Left","element":"ccf93a16-a718-4304-8a47-f3da022eb603"},"isManuallyLayouted":false,"flowType":"sequence"},"c46ecc85-e5b0-451e-a311-04359fabfae1":{"id":"c46ecc85-e5b0-451e-a311-04359fabfae1","name":"","type":"BPMNFlow","owner":null,"bounds":{"x":380,"y":-550,"width":100,"height":190},"path":[{"x":100,"y":190},{"x":100,"y":95},{"x":0,"y":95},{"x":0,"y":0}],"source":{"direction":"Up","element":"4bcb89cb-1d3a-45c3-a495-976a7ede3696"},"target":{"direction":"Down","element":"5742df27-c2fa-439b-b48f-d7e959ebdcca"},"isManuallyLayouted":false,"flowType":"message"}},"assessments":{}}';

        this.umlModel = JSON.parse(patchModel);
        this.hasElements = this.umlModel.elements && Object.values(this.umlModel.elements).length !== 0;

        this.explanation = this.submission.explanationText ?? '';
        this.subscribeToWebsockets();
        if (getLatestSubmissionResult(this.submission) && this.isAfterAssessmentDueDate) {
            this.result = getLatestSubmissionResult(this.submission);
        }
        this.resultWithComplaint = getFirstResultWithComplaint(this.submission);
        if (this.submission.submitted && this.result && this.result.completionDate) {
            this.modelingAssessmentService.getAssessment(this.submission.id!).subscribe((assessmentResult: Result) => {
                this.assessmentResult = assessmentResult;
                this.prepareAssessmentData();
            });
        }
        this.isLoading = false;
        this.guidedTourService.enableTourForExercise(this.modelingExercise, modelingTour, true);
    }

    /**
     * If the submission is submitted, subscribe to new results for the participation.
     * Otherwise, subscribe to the automatic submission (which happens when the submission is un-submitted and the exercise due date is over).
     */
    private subscribeToWebsockets(): void {
        if (this.submission && this.submission.id) {
            if (this.submission.submitted) {
                this.subscribeToNewResultsWebsocket();
            } else {
                this.subscribeToAutomaticSubmissionWebsocket();
            }
        }
    }

    /**
     * Subscribes to the websocket channel for automatic submissions. In the server the AutomaticSubmissionService regularly checks for unsubmitted submissions, if the
     * corresponding exercise has finished. If it has, the submission is automatically submitted and sent over this websocket channel. Here we listen to the channel and update the
     * view accordingly.
     */
    private subscribeToAutomaticSubmissionWebsocket(): void {
        if (!this.submission || !this.submission.id) {
            return;
        }
        this.automaticSubmissionWebsocketChannel = '/user/topic/modelingSubmission/' + this.submission.id;
        this.jhiWebsocketService.subscribe(this.automaticSubmissionWebsocketChannel);
        this.jhiWebsocketService.receive(this.automaticSubmissionWebsocketChannel).subscribe((submission: ModelingSubmission) => {
            if (submission.submitted) {
                this.submission = submission;
                if (this.submission.model) {
                    this.umlModel = JSON.parse(this.submission.model);
                    this.hasElements = this.umlModel.elements && Object.values(this.umlModel.elements).length !== 0;
                }
                if (getLatestSubmissionResult(this.submission) && getLatestSubmissionResult(this.submission)!.completionDate && this.isAfterAssessmentDueDate) {
                    this.modelingAssessmentService.getAssessment(this.submission.id!).subscribe((assessmentResult: Result) => {
                        this.assessmentResult = assessmentResult;
                        this.prepareAssessmentData();
                    });
                }
                this.alertService.info('artemisApp.modelingEditor.autoSubmit');
            }
        });
    }

    /**
     * Subscribes to the websocket channel for new results. When an assessment is submitted the new result is sent over this websocket channel. Here we listen to the channel
     * and show the new assessment information to the student.
     */
    private subscribeToNewResultsWebsocket(): void {
        if (!this.participation || !this.participation.id) {
            return;
        }
        this.resultUpdateListener = this.participationWebsocketService.subscribeForLatestResultOfParticipation(this.participation.id, true).subscribe((newResult: Result) => {
            if (newResult && newResult.completionDate) {
                this.assessmentResult = newResult;
                this.assessmentResult = this.modelingAssessmentService.convertResult(newResult);
                this.prepareAssessmentData();
                this.alertService.info('artemisApp.modelingEditor.newAssessment');
            }
        });
    }

    /**
     * This function sets and starts an auto-save timer that automatically saves changes
     * to the model after at most 60 seconds.
     */
    private setAutoSaveTimer(): void {
        this.autoSaveTimer = 0;
        // auto save of submission if there are changes
        this.autoSaveInterval = window.setInterval(() => {
            this.autoSaveTimer++;
            this.isChanged = !this.canDeactivate();
            if (this.autoSaveTimer >= AUTOSAVE_EXERCISE_INTERVAL && this.isChanged) {
                this.saveDiagram();
            }
        }, AUTOSAVE_CHECK_INTERVAL);
    }

    /**
     * Check every 2 seconds, if the user made changes for the submission in a team exercise: if yes, send it to the sever
     */
    private setupSubmissionStreamForTeam(): void {
        const teamSyncInterval = window.setInterval(() => {
            this.isChanged = !this.canDeactivate();
            if (this.isChanged) {
                // make sure this.submission includes the newest content of the apollon editor
                this.updateSubmissionWithCurrentValues();
                // notify the team sync component to send this.submission to the server (and all online team members)
                this.submissionChange.next(this.submission);
            }
        }, AUTOSAVE_TEAM_EXERCISE_INTERVAL);

        this.cleanup(() => clearInterval(teamSyncInterval));
    }

    /**
     * Emits submission patches when receiving patches from the modeling editor.
     * These patches need to be synced with other team members in team exercises.
     * The observable through which the patches are emitted is passed to the team sync
     * component, who then sends the patches to the server and other team members.
     * @param patch The patch to update the submission with.
     */
    onModelPatch(patch: Patch) {
        if (this.modelingExercise.teamMode) {
            const submissionPatch = new SubmissionPatch(patch);
            submissionPatch.participation = this.participation;
            if (submissionPatch.participation?.exercise) {
                submissionPatch.participation.exercise.studentParticipations = [];
            }
            this.submissionPatchObservable.next(Object.assign({}, submissionPatch));
        }
    }

    /**
     * Runs given cleanup logic when the component is destroyed.
     * @param teardown The cleanup logic to run when the component is destroyed.
     * @private
     */
    private cleanup(teardown: TeardownLogic) {
        this.subscription ??= new Subscription();
        this.subscription.add(teardown);
    }

    saveDiagram(): void {
        if (this.isSaving) {
            // don't execute the function if it is already currently executing
            return;
        }
        this.updateSubmissionWithCurrentValues();
        this.isSaving = true;
        this.autoSaveTimer = 0;

        if (this.submission.id) {
            this.modelingSubmissionService.update(this.submission, this.modelingExercise.id!).subscribe({
                next: (response) => {
                    this.submission = response.body!;
                    // reconnect so that the submission status is displayed correctly in the result.component
                    this.submission.participation!.submissions = [this.submission];
                    this.participationWebsocketService.addParticipation(this.submission.participation as StudentParticipation, this.modelingExercise);
                    this.result = getLatestSubmissionResult(this.submission);
                    this.onSaveSuccess();
                },
                error: (error: HttpErrorResponse) => this.onSaveError(error),
            });
        } else {
            this.modelingSubmissionService.create(this.submission, this.modelingExercise.id!).subscribe({
                next: (submission) => {
                    this.submission = submission.body!;
                    this.result = getLatestSubmissionResult(this.submission);
                    this.subscribeToAutomaticSubmissionWebsocket();
                    this.onSaveSuccess();
                },
                error: (error: HttpErrorResponse) => this.onSaveError(error),
            });
        }
    }

    submit(): void {
        if (this.isSaving) {
            // don't execute the function if it is already currently executing
            return;
        }
        this.updateSubmissionWithCurrentValues();
        if (this.isModelEmpty(this.submission.model)) {
            this.alertService.warning('artemisApp.modelingEditor.empty');
            return;
        }
        this.isSaving = true;
        this.autoSaveTimer = 0;
        if (this.submission.id) {
            this.modelingSubmissionService.update(this.submission, this.modelingExercise.id!).subscribe({
                next: (response) => {
                    this.submission = response.body!;

                    if (this.submission.model) {
                        this.umlModel = JSON.parse(this.submission.model);
                        this.hasElements = this.umlModel.elements && Object.values(this.umlModel.elements).length !== 0;
                    }

                    this.submissionChange.next(this.submission);
                    this.participation = this.submission.participation as StudentParticipation;
                    this.participation.exercise = this.modelingExercise;
                    // reconnect so that the submission status is displayed correctly in the result.component
                    this.submission.participation!.submissions = [this.submission];
                    this.participationWebsocketService.addParticipation(this.participation, this.modelingExercise);
                    this.modelingExercise.studentParticipations = [this.participation];
                    this.result = getLatestSubmissionResult(this.submission);
                    this.retryStarted = false;

                    if (this.isLate) {
                        this.alertService.warning('entity.action.submitDueDateMissedAlert');
                    } else {
                        this.alertService.success('entity.action.submitSuccessfulAlert');
                    }

                    this.subscribeToWebsockets();
                    if (this.automaticSubmissionWebsocketChannel) {
                        this.jhiWebsocketService.unsubscribe(this.automaticSubmissionWebsocketChannel);
                    }
                    this.onSaveSuccess();
                },
                error: (error: HttpErrorResponse) => this.onSaveError(error),
            });
        } else {
            this.modelingSubmissionService.create(this.submission, this.modelingExercise.id!).subscribe({
                next: (response) => {
                    this.submission = response.body!;
                    this.submissionChange.next(this.submission);
                    this.participation = this.submission.participation as StudentParticipation;
                    this.participation.exercise = this.modelingExercise;
                    this.modelingExercise.studentParticipations = [this.participation];
                    this.result = getLatestSubmissionResult(this.submission);
                    if (this.isLate) {
                        this.alertService.warning('artemisApp.modelingEditor.submitDueDateMissed');
                    } else {
                        this.alertService.success('artemisApp.modelingEditor.submitSuccessful');
                    }
                    this.subscribeToAutomaticSubmissionWebsocket();
                    this.onSaveSuccess();
                },
                error: (error: HttpErrorResponse) => this.onSaveError(error),
            });
        }
    }

    private onSaveSuccess() {
        this.isSaving = false;
        this.isChanged = !this.canDeactivate();
    }

    private onSaveError(error?: HttpErrorResponse) {
        if (error) {
            console.error(error.message);
        }
        this.alertService.error('artemisApp.modelingEditor.error');
        this.isSaving = false;
    }

    onReceiveSubmissionFromTeam(submission: ModelingSubmission) {
        submission.participation!.exercise = this.modelingExercise;
        submission.participation!.submissions = [submission];
        this.updateModelingSubmission(submission);
    }

    /**
     * This is called when the team sync component receives
     * patches from the server. Updates the modeling editor with the received patch.
     * @param submissionPatch
     */
    onReceiveSubmissionPatchFromTeam(submissionPatch: SubmissionPatch) {
        this.modelingEditor.importPatch(submissionPatch.patch);
    }

    private isModelEmpty(model?: string): boolean {
        const umlModel: UMLModel = model ? JSON.parse(model) : undefined;
        return !umlModel || !umlModel.elements || Object.values(umlModel.elements).length === 0;
    }

    ngOnDestroy(): void {
        this.subscription?.unsubscribe();
        clearInterval(this.autoSaveInterval);

        if (this.automaticSubmissionWebsocketChannel) {
            this.jhiWebsocketService.unsubscribe(this.automaticSubmissionWebsocketChannel);
        }
        if (this.resultUpdateListener) {
            this.resultUpdateListener.unsubscribe();
        }
    }

    /**
     * Check whether a assessmentResult exists and if, returns the unreferenced feedback of it
     */
    get unreferencedFeedback(): Feedback[] | undefined {
        if (this.assessmentResult?.feedbacks) {
            checkSubsequentFeedbackInAssessment(this.assessmentResult.feedbacks);
            return getUnreferencedFeedback(this.assessmentResult.feedbacks);
        }
        return undefined;
    }

    /**
     * Find "Referenced Feedback" item for Result, if it exists.
     */
    get referencedFeedback(): Feedback[] | undefined {
        if (this.assessmentResult?.feedbacks) {
            checkSubsequentFeedbackInAssessment(this.assessmentResult.feedbacks);
            return this.assessmentResult?.feedbacks?.filter((feedbackElement) => feedbackElement.reference != undefined);
        }
        return undefined;
    }

    /**
     * Updates the model of the submission with the current Apollon model state
     * and the explanation text of submission with current explanation if explanation is defined
     */
    updateSubmissionWithCurrentValues(): void {
        if (!this.submission) {
            this.submission = new ModelingSubmission();
        }
        this.submission.explanationText = this.explanation;
        if (!this.modelingEditor || !this.modelingEditor.getCurrentModel()) {
            return;
        }
        const umlModel = this.modelingEditor.getCurrentModel();
        this.hasElements = umlModel.elements && Object.values(umlModel.elements).length !== 0;
        const diagramJson = JSON.stringify(umlModel);
        if (this.submission && diagramJson) {
            this.submission.model = diagramJson;
        }
    }

    /**
     * Prepare assessment data for displaying the assessment information to the student.
     */
    private prepareAssessmentData(): void {
        this.initializeAssessmentInfo();
    }

    /**
     * Retrieves names for displaying the assessment and calculates the total score
     */
    private initializeAssessmentInfo(): void {
        if (this.assessmentResult?.feedbacks && this.umlModel) {
            this.assessmentsNames = getNamesForAssessments(this.assessmentResult, this.umlModel);
            let totalScore = 0;
            for (const feedback of this.assessmentResult.feedbacks) {
                totalScore += feedback.credits!;
            }
            this.totalScore = totalScore;
        }
    }

    /**
     * Handles changes of the model element selection in Apollon. This is used for displaying
     * only the feedback of the selected model elements.
     * @param selection the new selection
     */
    onSelectionChanged(selection: Selection) {
        this.selectedEntities = Object.entries(selection.elements)
            .filter(([, selected]) => selected)
            .map(([elementId]) => elementId);
        for (const selectedEntity of this.selectedEntities) {
            this.selectedEntities.push(...this.getSelectedChildren(selectedEntity));
        }
        this.selectedRelationships = Object.entries(selection.relationships)
            .filter(([, selected]) => selected)
            .map(([elementId]) => elementId);
    }

    /**
     * Returns the elementIds of all the children of the element with the given elementId
     * or an empty list, if no children exist for this element.
     */
    private getSelectedChildren(elementId: string): string[] {
        if (!this.umlModel || !this.umlModel.elements) {
            return [];
        }
        return Object.values(this.umlModel.elements)
            .filter((element) => element.owner === elementId)
            .map((element) => element.id);
    }

    /**
     * Checks whether a model element in the modeling editor is selected.
     */
    shouldBeDisplayed(feedback: Feedback): boolean {
        if ((!this.selectedEntities || this.selectedEntities.length === 0) && (!this.selectedRelationships || this.selectedRelationships.length === 0)) {
            return true;
        }
        const referencedModelType = feedback.referenceType! as UMLElementType;
        if (referencedModelType in UMLRelationshipType) {
            return this.selectedRelationships.indexOf(feedback.referenceId!) > -1;
        } else {
            return this.selectedEntities.indexOf(feedback.referenceId!) > -1;
        }
    }

    canDeactivate(): boolean {
        if (!this.modelingEditor || !this.modelingEditor.isApollonEditorMounted) {
            return true;
        }
        const model: UMLModel = this.modelingEditor.getCurrentModel();
        const explanationIsUpToDate = this.explanation === (this.submission.explanationText ?? '');
        return !this.modelHasUnsavedChanges(model) && explanationIsUpToDate;
    }

    /**
     * Checks whether there are pending changes in the current model. Returns true if there are unsaved changes, false otherwise.
     */
    private modelHasUnsavedChanges(model: UMLModel): boolean {
        if (!this.submission || !this.submission.model) {
            return Object.values(model.elements).length > 0 && JSON.stringify(model) !== '';
        } else if (this.submission && this.submission.model) {
            const currentModel = JSON.parse(this.submission.model);
            const versionMatch = currentModel.version === model.version;
            const modelMatch = stringifyIgnoringFields(currentModel, 'size') === stringifyIgnoringFields(model, 'size');
            return versionMatch && !modelMatch;
        }
        return false;
    }

    // displays the alert for confirming leaving the page if there are unsaved changes
    @HostListener('window:beforeunload', ['$event'])
    unloadNotification(event: any): void {
        if (!this.canDeactivate()) {
            event.returnValue = this.translateService.instant('pendingChanges');
        }
    }

    /**
     * counts the number of model elements
     * is used in the submit() function
     */
    calculateNumberOfModelElements(): number {
        if (this.submission && this.submission.model) {
            const umlModel = JSON.parse(this.submission.model);
            return umlModel.elements.length + umlModel.relationships.length;
        }
        return 0;
    }

    /**
     * The exercise is still active if it's due date hasn't passed yet.
     */
    get isActive(): boolean {
        return this.modelingExercise && !this.examMode && !hasExerciseDueDatePassed(this.modelingExercise, this.participation);
    }

    get submitButtonTooltip(): string {
        if (!this.isLate) {
            if (this.isActive && !this.modelingExercise.dueDate) {
                return 'entity.action.submitNoDueDateTooltip';
            } else if (this.isActive) {
                return 'entity.action.submitTooltip';
            } else {
                return 'entity.action.dueDateMissedTooltip';
            }
        }

        return 'entity.action.submitDueDateMissedTooltip';
    }
}
