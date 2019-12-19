import { ActiveFeatureToggles } from 'app/feature-toggle/feature-toggle.service';
import { GuidedTourMapping } from 'app/guided-tour/guided-tour-setting.model';
import { FileUploadExerciseSetting } from 'app/entities/file-upload-exercise/file-upload-exercise-setting.model';

export class ProfileInfo {
    activeProfiles: string[];
    ribbonEnv: string;
    inProduction: boolean;
    sentry?: { dsn: string };
    features: ActiveFeatureToggles;
    guidedTourMapping?: GuidedTourMapping;
    fileUploadExerciseSetting: FileUploadExerciseSetting;
}
