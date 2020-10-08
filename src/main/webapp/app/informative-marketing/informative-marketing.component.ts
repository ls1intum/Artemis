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
        const featureIntegratedProcess = new Feature(
            'informativeMarketing.instructor.feature.integratedProcess.title',
            'informativeMarketing.instructor.feature.integratedProcess.shortDescription',
            'informativeMarketing.instructor.feature.integratedProcess.descriptionTextOne',
            'informativeMarketing.instructor.feature.integratedProcess.icon',
        );
        featureIntegratedProcess.centerTextAndImageOne();

        if (this.targetAudience === targetAudience.INSTRUCTORS) {
            // Set up instructor features
            const featureConfiguration = new Feature(
                'informativeMarketing.instructor.feature.configuration.title',
                'informativeMarketing.instructor.feature.configuration.shortDescription',
                'informativeMarketing.instructor.feature.configuration.descriptionTextOne',
                'informativeMarketing.instructor.feature.configuration.icon',
                undefined,
                'informativeMarketing.instructor.feature.configuration.imageOne',
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
                'informativeMarketing.instructor.feature.sessionMonitoring.descriptionTextOne',
            );

            const featurePlagiarismDetection = new Feature(
                'informativeMarketing.instructor.feature.plagiarismDetection.title',
                'informativeMarketing.instructor.feature.plagiarismDetection.shortDescription',
                'informativeMarketing.instructor.feature.plagiarismDetection.descriptionTextOne',
                'informativeMarketing.instructor.feature.plagiarismDetection.icon',
                undefined,
                'informativeMarketing.instructor.feature.plagiarismDetection.imageOne',
                'informativeMarketing.instructor.feature.plagiarismDetection.imageTwo',
            );

            const featureAssessment = new Feature(
                'informativeMarketing.instructor.feature.assessment.title',
                'informativeMarketing.instructor.feature.assessment.shortDescription',
                'informativeMarketing.instructor.feature.assessment.descriptionTextOne',
                'informativeMarketing.instructor.feature.assessment.icon',
                'informativeMarketing.instructor.feature.assessment.descriptionTextOne',
                'informativeMarketing.instructor.feature.assessment.imageOne',
                'informativeMarketing.instructor.feature.assessment.imageTwo',
            );

            const featureAssessmentMonitoring = new Feature(
                'informativeMarketing.instructor.feature.assessmentMonitoring.title',
                'informativeMarketing.instructor.feature.assessmentMonitoring.shortDescription',
                'informativeMarketing.instructor.feature.assessmentMonitoring.descriptionTextOne',
                'informativeMarketing.instructor.feature.assessmentMonitoring.icon',
                'informativeMarketing.instructor.feature.assessmentMonitoring.descriptionTextOne',
                'informativeMarketing.instructor.feature.assessmentMonitoring.imageOne',
                'informativeMarketing.instructor.feature.assessmentMonitoring.imageTwo',
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
                'informativeMarketing.instructor.feature.complaints.descriptionTextOne',
                'informativeMarketing.instructor.feature.complaints.imageOne',
                'informativeMarketing.instructor.feature.complaints.imageTwo',
            );

            const featureStatistics = new Feature(
                'informativeMarketing.instructor.feature.statistics.title',
                'informativeMarketing.instructor.feature.statistics.shortDescription',
                'informativeMarketing.instructor.feature.statistics.descriptionTextOne',
                'informativeMarketing.instructor.feature.statistics.icon',
                'informativeMarketing.instructor.feature.statistics.descriptionTextOne',
                'informativeMarketing.instructor.feature.statistics.imageOne',
                'informativeMarketing.instructor.feature.statistics.imageTwo',
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
                featureGamification,
                featureComplaints,
                featureStatistics,
            ];
        } else {
            // set up student features
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
