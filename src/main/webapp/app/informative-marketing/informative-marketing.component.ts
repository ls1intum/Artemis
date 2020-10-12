import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-informative-marketing',
    templateUrl: './informative-marketing.component.html',
    styleUrls: ['./informative-marketing.scss'],
})
export class InformativeMarketingComponent implements OnInit {
    features: Feature[];
    targetAudience = targetAudience.INSTRUCTORS;

    constructor(private route: ActivatedRoute) {}

    /**
     * Initialises the informative marketing page either for students or for instructors, depending on the url.
     * Sets up the features to be displayed
     */
    ngOnInit(): void {
        if (this.route.snapshot.url[1]?.toString() === 'students') {
            this.targetAudience = targetAudience.STUDENTS;
        }

        if (this.targetAudience === targetAudience.INSTRUCTORS) {
            // Set up instructor features
            const featureIntegratedProcess = new Feature(
                'informativeMarketing.instructor.feature.integratedProcess.title',
                'informativeMarketing.instructor.feature.integratedProcess.shortDescription',
                'informativeMarketing.instructor.feature.integratedProcess.descriptionTextOne',
                'informativeMarketing.instructor.feature.integratedProcess.icon',
                undefined,
            );
            featureIntegratedProcess.centerTextAndImageOne();

            const featureConfiguration = new Feature(
                'informativeMarketing.instructor.feature.configuration.title',
                'informativeMarketing.instructor.feature.configuration.shortDescription',
                'informativeMarketing.instructor.feature.configuration.descriptionTextOne',
                'informativeMarketing.instructor.feature.configuration.icon',
                undefined,
                'informativeMarketing.instructor.feature.configuration.imageOne',
                'informativeMarketing.instructor.feature.configuration.imageTwo',
            );

            const featureExerciseTypes = new Feature(
                'informativeMarketing.instructor.feature.exerciseTypes.title',
                'informativeMarketing.instructor.feature.exerciseTypes.shortDescription',
                'informativeMarketing.instructor.feature.exerciseTypes.descriptionTextOne',
                'informativeMarketing.instructor.feature.exerciseTypes.icon',
                undefined,
                'informativeMarketing.instructor.feature.exerciseTypes.imageOne',
            );
            featureExerciseTypes.alignFirstImageLeft();

            const featureExerciseVariants = new Feature(
                'informativeMarketing.instructor.feature.exerciseVariants.title',
                'informativeMarketing.instructor.feature.exerciseVariants.shortDescription',
                'informativeMarketing.instructor.feature.exerciseVariants.descriptionTextOne',
                'informativeMarketing.instructor.feature.exerciseVariants.icon',
                undefined,
                'informativeMarketing.instructor.feature.exerciseVariants.imageOne',
            );
            featureExerciseVariants.centerTextAndImageOne();

            const featureSessionMonitoring = new Feature(
                'informativeMarketing.instructor.feature.sessionMonitoring.title',
                'informativeMarketing.instructor.feature.sessionMonitoring.shortDescription',
                'informativeMarketing.instructor.feature.sessionMonitoring.descriptionTextOne',
                'informativeMarketing.instructor.feature.sessionMonitoring.icon',
            );

            const featurePlagiarismDetection = new Feature(
                'informativeMarketing.instructor.feature.plagiarismDetection.title',
                'informativeMarketing.instructor.feature.plagiarismDetection.shortDescription',
                'informativeMarketing.instructor.feature.plagiarismDetection.descriptionTextOne',
                'informativeMarketing.instructor.feature.plagiarismDetection.icon',
                undefined,
                'informativeMarketing.instructor.feature.plagiarismDetection.imageOne',
            );

            const featureAssessment = new Feature(
                'informativeMarketing.instructor.feature.assessment.title',
                'informativeMarketing.instructor.feature.assessment.shortDescription',
                'informativeMarketing.instructor.feature.assessment.descriptionTextOne',
                'informativeMarketing.instructor.feature.assessment.icon',
                'informativeMarketing.instructor.feature.assessment.descriptionTextTwo',
                'informativeMarketing.instructor.feature.assessment.imageOne',
            );

            const featureAssessmentMonitoring = new Feature(
                'informativeMarketing.instructor.feature.assessmentMonitoring.title',
                'informativeMarketing.instructor.feature.assessmentMonitoring.shortDescription',
                'informativeMarketing.instructor.feature.assessmentMonitoring.descriptionTextOne',
                'informativeMarketing.instructor.feature.assessmentMonitoring.icon',
                undefined,
                'informativeMarketing.instructor.feature.assessmentMonitoring.imageOne',
            );
            featureAssessmentMonitoring.alignFirstImageLeft();

            const featureGamification = new Feature(
                'informativeMarketing.instructor.feature.assessmentGamification.title',
                'informativeMarketing.instructor.feature.assessmentGamification.shortDescription',
                'informativeMarketing.instructor.feature.assessmentGamification.descriptionTextOne',
                'informativeMarketing.instructor.feature.assessmentGamification.icon',
                'informativeMarketing.instructor.feature.assessmentGamification.descriptionTextOne',
                'informativeMarketing.instructor.feature.assessmentGamification.imageOne',
                'informativeMarketing.instructor.feature.assessmentGamification.imageTwo',
            );

            const featureComplaints = new Feature(
                'informativeMarketing.instructor.feature.complaints.title',
                'informativeMarketing.instructor.feature.complaints.shortDescription',
                'informativeMarketing.instructor.feature.complaints.descriptionTextOne',
                'informativeMarketing.instructor.feature.complaints.icon',
                undefined,
                'informativeMarketing.instructor.feature.complaints.imageOne',
            );
            featureComplaints.alignFirstImageLeft();

            const featureStatistics = new Feature(
                'informativeMarketing.instructor.feature.statistics.title',
                'informativeMarketing.instructor.feature.statistics.shortDescription',
                'informativeMarketing.instructor.feature.statistics.descriptionTextOne',
                'informativeMarketing.instructor.feature.statistics.icon',
                undefined,
                'informativeMarketing.instructor.feature.statistics.imageOne',
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
        } else {
            // set up student features
            const featurelocalIDE = new Feature(
                'informativeMarketing.students.feature.localIDE.title',
                'informativeMarketing.students.feature.localIDE.shortDescription',
                'informativeMarketing.students.feature.localIDE.descriptionTextOne',
                'informativeMarketing.students.feature.localIDE.icon',
                undefined,
            );

            const featureCodeEditor = new Feature(
                'informativeMarketing.students.feature.codeEditor.title',
                'informativeMarketing.students.feature.codeEditor.shortDescription',
                'informativeMarketing.students.feature.codeEditor.descriptionTextOne',
                'informativeMarketing.students.feature.codeEditor.icon',
                undefined,
            );

            const featureTextEditor = new Feature(
                'informativeMarketing.students.feature.textEditor.title',
                'informativeMarketing.students.feature.textEditor.shortDescription',
                'informativeMarketing.students.feature.textEditor.descriptionTextOne',
                'informativeMarketing.students.feature.textEditor.icon',
                undefined,
            );

            const featureapollonEditor = new Feature(
                'informativeMarketing.students.feature.apollonEditor.title',
                'informativeMarketing.students.feature.apollonEditor.shortDescription',
                'informativeMarketing.students.feature.apollonEditor.descriptionTextOne',
                'informativeMarketing.students.feature.apollonEditor.icon',
                undefined,
            );

            const featureQuizExercises = new Feature(
                'informativeMarketing.students.feature.quizExercises.title',
                'informativeMarketing.students.feature.quizExercises.shortDescription',
                'informativeMarketing.students.feature.quizExercises.descriptionTextOne',
                'informativeMarketing.students.feature.quizExercises.icon',
                undefined,
            );

            const featureLogin = new Feature(
                'informativeMarketing.students.feature.login.title',
                'informativeMarketing.students.feature.login.shortDescription',
                'informativeMarketing.students.feature.login.descriptionTextOne',
                'informativeMarketing.students.feature.login.icon',
                undefined,
            );

            const featureUserInterface = new Feature(
                'informativeMarketing.students.feature.userInterface.title',
                'informativeMarketing.students.feature.userInterface.shortDescription',
                'informativeMarketing.students.feature.userInterface.descriptionTextOne',
                'informativeMarketing.students.feature.userInterface.icon',
                'informativeMarketing.students.feature.userInterface.descriptionTextTwo',
            );

            const featureConduction = new Feature(
                'informativeMarketing.students.feature.conduction.title',
                'informativeMarketing.students.feature.conduction.shortDescription',
                'informativeMarketing.students.feature.conduction.descriptionTextOne',
                'informativeMarketing.students.feature.conduction.icon',
                undefined,
            );

            const featureSummary = new Feature(
                'informativeMarketing.students.feature.summary.title',
                'informativeMarketing.students.feature.summary.shortDescription',
                'informativeMarketing.students.feature.summary.descriptionTextOne',
                'informativeMarketing.students.feature.summary.icon',
                undefined,
            );

            const featureOnlineReview = new Feature(
                'informativeMarketing.students.feature.onlineReview.title',
                'informativeMarketing.students.feature.onlineReview.shortDescription',
                'informativeMarketing.students.feature.onlineReview.descriptionTextOne',
                'informativeMarketing.students.feature.onlineReview.icon',
                undefined,
            );

            const featureBrowserCompatibility = new Feature(
                'informativeMarketing.students.feature.browserCompatibility.title',
                'informativeMarketing.students.feature.browserCompatibility.shortDescription',
                'informativeMarketing.students.feature.browserCompatibility.descriptionTextOne',
                'informativeMarketing.students.feature.browserCompatibility.icon',
                undefined,
            );

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
