import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-feature-overview',
    templateUrl: './feature-overview.component.html',
    styleUrls: ['./feature-overview.scss'],
})
export class FeatureOverviewComponent implements OnInit {
    features: Feature[];
    targetAudience = targetAudience.INSTRUCTORS;

    constructor(private route: ActivatedRoute) {}

    /**
     * Initialises the informative marketing page either for students or for instructors, depending on the url.
     * Sets up the features to be displayed
     */
    ngOnInit(): void {
        if (this.route.snapshot.url[0]?.toString() === 'students') {
            this.targetAudience = targetAudience.STUDENTS;
        }

        if (this.targetAudience === targetAudience.INSTRUCTORS) {
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
            'featureOverview.students.feature.localIDE.icon',
            undefined,
            'featureOverview.students.feature.localIDE.imageOne',
        );
        featurelocalIDE.centerTextAndImageOne();

        const featureCodeEditor = new Feature(
            'featureOverview.students.feature.codeEditor.title',
            'featureOverview.students.feature.codeEditor.shortDescription',
            'featureOverview.students.feature.codeEditor.descriptionTextOne',
            'featureOverview.students.feature.codeEditor.icon',
            undefined,
            'featureOverview.students.feature.codeEditor.imageOne',
        );

        const featureTextEditor = new Feature(
            'featureOverview.students.feature.textEditor.title',
            'featureOverview.students.feature.textEditor.shortDescription',
            'featureOverview.students.feature.textEditor.descriptionTextOne',
            'featureOverview.students.feature.textEditor.icon',
            undefined,
            'featureOverview.students.feature.textEditor.imageOne',
        );
        featureTextEditor.centerTextAndImageOne();

        const featureapollonEditor = new Feature(
            'featureOverview.students.feature.apollonEditor.title',
            'featureOverview.students.feature.apollonEditor.shortDescription',
            'featureOverview.students.feature.apollonEditor.descriptionTextOne',
            'featureOverview.students.feature.apollonEditor.icon',
            undefined,
            'featureOverview.students.feature.apollonEditor.imageOne',
        );

        const featureQuizExercises = new Feature(
            'featureOverview.students.feature.quizExercises.title',
            'featureOverview.students.feature.quizExercises.shortDescription',
            'featureOverview.students.feature.quizExercises.descriptionTextOne',
            'featureOverview.students.feature.quizExercises.icon',
            undefined,
            'featureOverview.students.feature.quizExercises.imageOne',
        );
        featureQuizExercises.alignFirstImageLeft();

        const featureLogin = new Feature(
            'featureOverview.students.feature.login.title',
            'featureOverview.students.feature.login.shortDescription',
            'featureOverview.students.feature.login.descriptionTextOne',
            'featureOverview.students.feature.login.icon',
            undefined,
            'featureOverview.students.feature.login.imageOne',
        );
        featureLogin.centerTextAndImageOne();

        const featureUserInterface = new Feature(
            'featureOverview.students.feature.userInterface.title',
            'featureOverview.students.feature.userInterface.shortDescription',
            'featureOverview.students.feature.userInterface.descriptionTextOne',
            'featureOverview.students.feature.userInterface.icon',
            'featureOverview.students.feature.userInterface.descriptionTextTwo',
            'featureOverview.students.feature.userInterface.imageOne',
        );

        const featureConduction = new Feature(
            'featureOverview.students.feature.conduction.title',
            'featureOverview.students.feature.conduction.shortDescription',
            'featureOverview.students.feature.conduction.descriptionTextOne',
            'featureOverview.students.feature.conduction.icon',
            undefined,
        );

        const featureSummary = new Feature(
            'featureOverview.students.feature.summary.title',
            'featureOverview.students.feature.summary.shortDescription',
            'featureOverview.students.feature.summary.descriptionTextOne',
            'featureOverview.students.feature.summary.icon',
            undefined,
            'featureOverview.students.feature.summary.imageOne',
        );

        const featureOnlineReview = new Feature(
            'featureOverview.students.feature.onlineReview.title',
            'featureOverview.students.feature.onlineReview.shortDescription',
            'featureOverview.students.feature.onlineReview.descriptionTextOne',
            'featureOverview.students.feature.onlineReview.icon',
            undefined,
            'featureOverview.students.feature.onlineReview.imageOne',
        );

        const featureBrowserCompatibility = new Feature(
            'featureOverview.students.feature.browserCompatibility.title',
            'featureOverview.students.feature.browserCompatibility.shortDescription',
            'featureOverview.students.feature.browserCompatibility.descriptionTextOne',
            'featureOverview.students.feature.browserCompatibility.icon',
            undefined,
        );
        featureBrowserCompatibility.centerTextAndImageOne();

        this.features = [
            featureConduction,
            featureUserInterface,
            featureCodeEditor,
            featurelocalIDE,
            featureapollonEditor,
            featureTextEditor,
            featureQuizExercises,
            featureSummary,
            featureOnlineReview,
            featureLogin,
            featureBrowserCompatibility,
        ];
    }

    private setupInstructorFeatures() {
        // Set up instructor features
        const featureIntegratedProcess = new Feature(
            'featureOverview.instructor.feature.integratedProcess.title',
            'featureOverview.instructor.feature.integratedProcess.shortDescription',
            'featureOverview.instructor.feature.integratedProcess.descriptionTextOne',
            'featureOverview.instructor.feature.integratedProcess.icon',
            undefined,
        );
        featureIntegratedProcess.centerTextAndImageOne();

        const featureConfiguration = new Feature(
            'featureOverview.instructor.feature.configuration.title',
            'featureOverview.instructor.feature.configuration.shortDescription',
            'featureOverview.instructor.feature.configuration.descriptionTextOne',
            'featureOverview.instructor.feature.configuration.icon',
            undefined,
            'featureOverview.instructor.feature.configuration.imageOne',
            'featureOverview.instructor.feature.configuration.imageTwo',
        );

        const featureExerciseTypes = new Feature(
            'featureOverview.instructor.feature.exerciseTypes.title',
            'featureOverview.instructor.feature.exerciseTypes.shortDescription',
            'featureOverview.instructor.feature.exerciseTypes.descriptionTextOne',
            'featureOverview.instructor.feature.exerciseTypes.icon',
            undefined,
            'featureOverview.instructor.feature.exerciseTypes.imageOne',
        );
        featureExerciseTypes.alignFirstImageLeft();

        const featureExerciseVariants = new Feature(
            'featureOverview.instructor.feature.exerciseVariants.title',
            'featureOverview.instructor.feature.exerciseVariants.shortDescription',
            'featureOverview.instructor.feature.exerciseVariants.descriptionTextOne',
            'featureOverview.instructor.feature.exerciseVariants.icon',
            undefined,
            'featureOverview.instructor.feature.exerciseVariants.imageOne',
        );
        featureExerciseVariants.centerTextAndImageOne();

        const featureSessionMonitoring = new Feature(
            'featureOverview.instructor.feature.sessionMonitoring.title',
            'featureOverview.instructor.feature.sessionMonitoring.shortDescription',
            'featureOverview.instructor.feature.sessionMonitoring.descriptionTextOne',
            'featureOverview.instructor.feature.sessionMonitoring.icon',
        );

        const featurePlagiarismDetection = new Feature(
            'featureOverview.instructor.feature.plagiarismDetection.title',
            'featureOverview.instructor.feature.plagiarismDetection.shortDescription',
            'featureOverview.instructor.feature.plagiarismDetection.descriptionTextOne',
            'featureOverview.instructor.feature.plagiarismDetection.icon',
            undefined,
            'featureOverview.instructor.feature.plagiarismDetection.imageOne',
        );

        const featureAssessment = new Feature(
            'featureOverview.instructor.feature.assessment.title',
            'featureOverview.instructor.feature.assessment.shortDescription',
            'featureOverview.instructor.feature.assessment.descriptionTextOne',
            'featureOverview.instructor.feature.assessment.icon',
            'featureOverview.instructor.feature.assessment.descriptionTextTwo',
            'featureOverview.instructor.feature.assessment.imageOne',
        );

        const featureAssessmentMonitoring = new Feature(
            'featureOverview.instructor.feature.assessmentMonitoring.title',
            'featureOverview.instructor.feature.assessmentMonitoring.shortDescription',
            'featureOverview.instructor.feature.assessmentMonitoring.descriptionTextOne',
            'featureOverview.instructor.feature.assessmentMonitoring.icon',
            undefined,
            'featureOverview.instructor.feature.assessmentMonitoring.imageOne',
        );
        featureAssessmentMonitoring.alignFirstImageLeft();

        const featureGamification = new Feature(
            'featureOverview.instructor.feature.assessmentGamification.title',
            'featureOverview.instructor.feature.assessmentGamification.shortDescription',
            'featureOverview.instructor.feature.assessmentGamification.descriptionTextOne',
            'featureOverview.instructor.feature.assessmentGamification.icon',
            'featureOverview.instructor.feature.assessmentGamification.descriptionTextOne',
            'featureOverview.instructor.feature.assessmentGamification.imageOne',
            'featureOverview.instructor.feature.assessmentGamification.imageTwo',
        );

        const featureComplaints = new Feature(
            'featureOverview.instructor.feature.complaints.title',
            'featureOverview.instructor.feature.complaints.shortDescription',
            'featureOverview.instructor.feature.complaints.descriptionTextOne',
            'featureOverview.instructor.feature.complaints.icon',
            undefined,
            'featureOverview.instructor.feature.complaints.imageOne',
        );
        featureComplaints.alignFirstImageLeft();

        const featureStatistics = new Feature(
            'featureOverview.instructor.feature.statistics.title',
            'featureOverview.instructor.feature.statistics.shortDescription',
            'featureOverview.instructor.feature.statistics.descriptionTextOne',
            'featureOverview.instructor.feature.statistics.icon',
            undefined,
            'featureOverview.instructor.feature.statistics.imageOne',
        );
        this.features = [
            featureIntegratedProcess,
            featureConfiguration,
            featureExerciseTypes,
            featureExerciseVariants,
            featureSessionMonitoring,
            featurePlagiarismDetection,
            featureAssessment,
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
            const y = element.getBoundingClientRect().top + window.pageYOffset;
            window.scrollTo({ top: y, behavior: 'smooth' });
        }
    }
}

enum targetAudience {
    INSTRUCTORS = 'instructor',
    STUDENTS = 'students',
}

class Feature {
    title: string;
    shortDescription: string;
    descriptionTextOne: string;
    textOneCentered = false;
    descriptionTextTwo?: string;
    imageOne?: string;
    imageTwo?: string;
    firstImageLeft = false;
    icon: string;
    id: string;

    constructor(title: string, shortDescription: string, descriptionTextOne: string, icon: string, descriptionTextTwo?: string, imageOne?: string, imageTwo?: string) {
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
        return ':' + Math.random().toString(36).substr(2, 9);
    }

    /**
     * Centers the text and first image.
     * Note: Only has an effect if there is no second text
     */
    centerTextAndImageOne(): void {
        this.textOneCentered = true;
    }

    /**
     * Align the first image to the left, instead of the right
     */
    alignFirstImageLeft() {
        this.firstImageLeft = true;
    }
}
