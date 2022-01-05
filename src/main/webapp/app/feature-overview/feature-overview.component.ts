import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';

@Component({
    selector: 'jhi-feature-overview',
    templateUrl: './feature-overview.component.html',
    styleUrls: ['./feature-overview.scss'],
})
export class FeatureOverviewComponent implements OnInit {
    features: Feature[];
    targetAudience = TargetAudience.INSTRUCTORS;

    constructor(private route: ActivatedRoute, private profileService: ProfileService) {}

    /**
     * Initialises the feature overview page either for students or for instructors, depending on the url.
     * Sets up the features to be displayed
     */
    ngOnInit(): void {
        if (this.route.snapshot.url[0]?.toString() === 'students') {
            this.targetAudience = TargetAudience.STUDENTS;
        }

        if (this.targetAudience === TargetAudience.INSTRUCTORS) {
            this.setupInstructorFeatures();
        } else {
            this.setupStudentFeatures();
        }
    }

    private setupStudentFeatures() {
        // set up student features
        const featurelocalIDE = new Feature(
            'featureOverview.students.feature.localIDE.title',
            'featureOverview.students.feature.localIDE.shortDescription',
            'featureOverview.students.feature.localIDE.descriptionTextOne',
            'code-branch',
            undefined,
            '/content/images/feature-overview/students/clone_repository.png',
        );
        featurelocalIDE.centerTextAndImageOne();

        const featureCodeEditor = new Feature(
            'featureOverview.students.feature.codeEditor.title',
            'featureOverview.students.feature.codeEditor.shortDescription',
            'featureOverview.students.feature.codeEditor.descriptionTextOne',
            'code',
            undefined,
            '/content/images/feature-overview/students/code_editor.png',
        );

        const featureTextEditor = new Feature(
            'featureOverview.students.feature.textEditor.title',
            'featureOverview.students.feature.textEditor.shortDescription',
            'featureOverview.students.feature.textEditor.descriptionTextOne',
            'file',
            undefined,
            '/content/images/feature-overview/students/text_editor.png',
        );
        featureTextEditor.centerTextAndImageOne();

        const featureApollonEditor = new Feature(
            'featureOverview.students.feature.apollonEditor.title',
            'featureOverview.students.feature.apollonEditor.shortDescription',
            'featureOverview.students.feature.apollonEditor.descriptionTextOne',
            'object-group',
            undefined,
            '/content/images/feature-overview/students/modeling_editor.png',
        );

        const featureQuizExercises = new Feature(
            'featureOverview.students.feature.quizExercises.title',
            'featureOverview.students.feature.quizExercises.shortDescription',
            'featureOverview.students.feature.quizExercises.descriptionTextOne',
            'check-square',
            undefined,
            '/content/images/feature-overview/students/quiz_exercises.png',
        );
        featureQuizExercises.alignFirstImageLeft();

        const featureLogin = new Feature(
            'featureOverview.students.feature.login.title',
            'featureOverview.students.feature.login.shortDescription',
            'featureOverview.students.feature.login.descriptionTextOne',
            'sign-in-alt',
            undefined,
            '/content/images/feature-overview/students/login.png',
        );
        featureLogin.centerAndShrinkImage();

        const featureUserInterface = new Feature(
            'featureOverview.students.feature.userInterface.title',
            'featureOverview.students.feature.userInterface.shortDescription',
            'featureOverview.students.feature.userInterface.descriptionTextOne',
            'puzzle-piece',
            'featureOverview.students.feature.userInterface.descriptionTextTwo',
            '/content/images/feature-overview/students/user_interface.png',
        );

        const featureConduction = new Feature(
            'featureOverview.students.feature.conduction.title',
            'featureOverview.students.feature.conduction.shortDescription',
            'featureOverview.students.feature.conduction.descriptionTextOne',
            'play',
            undefined,
            '/content/images/feature-overview/students/online_exams.png',
        );
        featureConduction.centerAndExpandImage();

        const featureOffline = new Feature(
            'featureOverview.students.feature.offline.title',
            'featureOverview.students.feature.offline.shortDescription',
            'featureOverview.students.feature.offline.descriptionTextOne',
            'signal',
        );

        const featureSummary = new Feature(
            'featureOverview.students.feature.summary.title',
            'featureOverview.students.feature.summary.shortDescription',
            'featureOverview.students.feature.summary.descriptionTextOne',
            'th-list',
            undefined,
            '/content/images/feature-overview/students/summary.png',
        );

        const featureQualityAndFair = new Feature(
            'featureOverview.students.feature.qualityAndFair.title',
            'featureOverview.students.feature.qualityAndFair.shortDescription',
            'featureOverview.students.feature.qualityAndFair.descriptionTextOne',
            'eye',
        );

        const featureOnlineReview = new Feature(
            'featureOverview.students.feature.onlineReview.title',
            'featureOverview.students.feature.onlineReview.shortDescription',
            'featureOverview.students.feature.onlineReview.descriptionTextOne',
            'comment',
            undefined,
            '/content/images/feature-overview/students/complaint.png',
        );

        this.features = [
            featureConduction,
            featureOffline,
            featureUserInterface,
            featureCodeEditor,
            featurelocalIDE,
            featureApollonEditor,
            featureTextEditor,
            featureQuizExercises,
            featureSummary,
            featureQualityAndFair,
            featureOnlineReview,
        ];

        // only add login feature for tum accounts
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo) {
                const accountName = profileInfo.accountName;
                if (accountName === 'TUM') {
                    this.features.push(featureLogin);
                }
            }
        });
    }

    private setupInstructorFeatures() {
        // Set up instructor features
        const featureCreateConductAssess = new Feature(
            'featureOverview.instructor.feature.createConductAssess.title',
            'featureOverview.instructor.feature.createConductAssess.shortDescription',
            'featureOverview.instructor.feature.createConductAssess.descriptionTextOne',
            'cubes',
            undefined,
            '/content/images/feature-overview/instructors/create_conduct_assess.png',
        );
        featureCreateConductAssess.centerAndExpandImage();

        const featureConfiguration = new Feature(
            'featureOverview.instructor.feature.configuration.title',
            'featureOverview.instructor.feature.configuration.shortDescription',
            'featureOverview.instructor.feature.configuration.descriptionTextOne',
            'cogs',
            undefined,
            '/content/images/feature-overview/instructors/import_students.png',
            '/content/images/feature-overview/instructors/fully_configurable.png',
        );

        const featureExerciseTypes = new Feature(
            'featureOverview.instructor.feature.exerciseTypes.title',
            'featureOverview.instructor.feature.exerciseTypes.shortDescription',
            'featureOverview.instructor.feature.exerciseTypes.descriptionTextOne',
            'tasks',
            undefined,
            '/content/images/feature-overview/instructors/multiple_exercises.png',
        );
        featureExerciseTypes.alignFirstImageLeft();

        const featureExerciseVariants = new Feature(
            'featureOverview.instructor.feature.exerciseVariants.title',
            'featureOverview.instructor.feature.exerciseVariants.shortDescription',
            'featureOverview.instructor.feature.exerciseVariants.descriptionTextOne',
            'copy',
            undefined,
            '/content/images/feature-overview/instructors/exercise_variants.png',
        );
        featureExerciseVariants.centerTextAndImageOne();

        const featureTestRuns = new Feature(
            'featureOverview.instructor.feature.testRunConduction.title',
            'featureOverview.instructor.feature.testRunConduction.shortDescription',
            'featureOverview.instructor.feature.testRunConduction.descriptionTextOne',
            'pencil-alt',
            undefined,
        );

        const featureSessionMonitoring = new Feature(
            'featureOverview.instructor.feature.sessionMonitoring.title',
            'featureOverview.instructor.feature.sessionMonitoring.shortDescription',
            'featureOverview.instructor.feature.sessionMonitoring.descriptionTextOne',
            'hdd',
        );

        const featurePlagiarismDetection = new Feature(
            'featureOverview.instructor.feature.plagiarismDetection.title',
            'featureOverview.instructor.feature.plagiarismDetection.shortDescription',
            'featureOverview.instructor.feature.plagiarismDetection.descriptionTextOne',
            'user-secret',
            undefined,
            '/content/images/feature-overview/instructors/plagiarism.png',
        );

        const featureAnonymousAssessment = new Feature(
            'featureOverview.instructor.feature.anonymousAssessment.title',
            'featureOverview.instructor.feature.anonymousAssessment.shortDescription',
            'featureOverview.instructor.feature.anonymousAssessment.descriptionTextOne',
            'shield-alt',
            undefined,
            '/content/images/feature-overview/instructors/anonymous_assessment.png',
        );

        const featureAutomaticAssessment = new Feature(
            'featureOverview.instructor.feature.automaticAssessment.title',
            'featureOverview.instructor.feature.automaticAssessment.shortDescription',
            'featureOverview.instructor.feature.automaticAssessment.descriptionTextOne',
            'magic',
        );

        const featureReviewIndividualExams = new Feature(
            'featureOverview.instructor.feature.reviewIndividualExams.title',
            'featureOverview.instructor.feature.reviewIndividualExams.shortDescription',
            'featureOverview.instructor.feature.reviewIndividualExams.descriptionTextOne',
            'search-plus',
            undefined,
            '/content/images/feature-overview/instructors/student_exams.png',
        );

        const featureAssessmentMonitoring = new Feature(
            'featureOverview.instructor.feature.assessmentMonitoring.title',
            'featureOverview.instructor.feature.assessmentMonitoring.shortDescription',
            'featureOverview.instructor.feature.assessmentMonitoring.descriptionTextOne',
            'microchip',
            undefined,
            '/content/images/feature-overview/instructors/progress_monitoring.png',
        );
        featureAssessmentMonitoring.alignFirstImageLeft();

        const featureComplaints = new Feature(
            'featureOverview.instructor.feature.complaints.title',
            'featureOverview.instructor.feature.complaints.shortDescription',
            'featureOverview.instructor.feature.complaints.descriptionTextOne',
            'question',
            undefined,
            '/content/images/feature-overview/instructors/complaint_response.png',
        );
        featureComplaints.alignFirstImageLeft();

        const featureStatistics = new Feature(
            'featureOverview.instructor.feature.statistics.title',
            'featureOverview.instructor.feature.statistics.shortDescription',
            'featureOverview.instructor.feature.statistics.descriptionTextOne',
            'chart-pie',
            undefined,
            '/content/images/feature-overview/instructors/exam_statistics.png',
        );
        this.features = [
            featureCreateConductAssess,
            featureConfiguration,
            featureExerciseTypes,
            featureExerciseVariants,
            featureTestRuns,
            featureSessionMonitoring,
            featurePlagiarismDetection,
            featureAnonymousAssessment,
            featureAutomaticAssessment,
            featureReviewIndividualExams,
            featureAssessmentMonitoring,
            featureComplaints,
            featureStatistics,
        ];
    }

    navigateToFeature(featureId: string): void {
        // get html element for feature
        const element = document.getElementById('feature' + featureId);
        if (element) {
            // scroll to correct y
            const y = element.getBoundingClientRect().top + window.scrollY;
            window.scrollTo({ top: y, behavior: 'smooth' });
        }
    }
}

export enum TargetAudience {
    INSTRUCTORS = 'instructor',
    STUDENTS = 'students',
}

class Feature {
    title: string;
    shortDescription: string;
    descriptionTextOne: string;
    centered = false;
    expandedImage = false;
    shrunkImage = false;
    descriptionTextTwo?: string;
    imageOne?: string;
    imageTwo?: string;
    firstImageLeft = false;
    icon: IconProp;
    id: string;

    constructor(title: string, shortDescription: string, descriptionTextOne: string, icon: IconProp, descriptionTextTwo?: string, imageOne?: string, imageTwo?: string) {
        this.title = title;
        this.shortDescription = shortDescription;
        this.descriptionTextOne = descriptionTextOne;
        this.descriptionTextTwo = descriptionTextTwo;
        this.imageOne = imageOne;
        this.imageTwo = imageTwo;
        this.icon = icon;
        this.id = this.setId();
    }

    /**
     * Math.random should be unique because of its seeding algorithm.
     * Convert it to base 36 (numbers + letters), and grab the first 9 characters after the decimal.
     * @private
     */
    setId(): string {
        return '_' + Math.random().toString(36).substr(2, 9);
    }

    /**
     * Centers the text and first image.
     * Note: Only has an effect if there is no second text
     */
    centerTextAndImageOne(): void {
        this.centered = true;
    }

    /**
     * Centers the text and first image.
     * Additionally it makes the first image larger. Use for big images which need more space.
     * Note: Only has an effect if there is no second text
     */
    centerAndExpandImage(): void {
        this.centered = true;
        this.expandedImage = true;
    }

    /**
     * Centers the text and first image.
     * Additionally it makes the first image smaller. Use for low resolution images.
     * Note: Only has an effect if there is no second text
     */
    centerAndShrinkImage(): void {
        this.centered = true;
        this.shrunkImage = true;
    }

    /**
     * Align the first image to the left, instead of the right
     */
    alignFirstImageLeft() {
        this.firstImageLeft = true;
    }
}
