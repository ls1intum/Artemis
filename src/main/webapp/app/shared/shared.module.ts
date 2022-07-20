import { NgModule } from '@angular/core';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { CircularProgressBarComponent } from 'app/shared/circular-progress-bar/circular-progress-bar.component';
import { JhiConnectionStatusComponent } from 'app/shared/connection-status/connection-status.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { DeleteDialogComponent } from 'app/shared/delete-dialog/delete-dialog.component';
import { SecureLinkDirective } from 'app/shared/http/secure-link.directive';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { ArtemisSharedPipesModule } from 'app/shared/pipes/shared-pipes.module';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisSharedLibsModule } from 'app/shared/shared-libs.module';
import { CustomMaxDirective } from 'app/shared/validators/custom-max-validator.directive';
import { CustomMinDirective } from 'app/shared/validators/custom-min-validator.directive';
import { OrganizationSelectorComponent } from './organization-selector/organization-selector.component';
import { AdditionalFeedbackComponent } from './additional-feedback/additional-feedback.component';
import { ResizeableContainerComponent } from './resizeable-container/resizeable-container.component';
import { RouterModule } from '@angular/router';
import { ExtensionPointDirective } from 'app/shared/extension-point/extension-point.directive';
import { CustomPatternValidatorDirective } from 'app/shared/validators/custom-pattern-validator.directive';
import { ItemCountComponent } from 'app/shared/pagination/item-count.component';
import { ConsistencyCheckComponent } from 'app/shared/consistency-check/consistency-check.component';
import { AssessmentWarningComponent } from 'app/assessment/assessment-warning/assessment-warning.component';
import { JhiConnectionWarningComponent } from 'app/shared/connection-warning/connection-warning.component';

@NgModule({
    imports: [ArtemisSharedLibsModule, ArtemisSharedCommonModule, ArtemisSharedPipesModule, RouterModule],
    declarations: [
        CircularProgressBarComponent,
        AdditionalFeedbackComponent,
        HasAnyAuthorityDirective,
        ExtensionPointDirective,
        SecuredImageComponent,
        DeleteButtonDirective,
        DeleteDialogComponent,
        ResizeableContainerComponent,
        SecureLinkDirective,
        JhiConnectionStatusComponent,
        JhiConnectionWarningComponent,
        OrganizationSelectorComponent,
        CustomMinDirective,
        CustomMaxDirective,
        CustomPatternValidatorDirective,
        ItemCountComponent,
        ConsistencyCheckComponent,
        AssessmentWarningComponent,
    ],
    exports: [
        ArtemisSharedLibsModule,
        ArtemisSharedCommonModule,
        ArtemisSharedPipesModule,
        CircularProgressBarComponent,
        AdditionalFeedbackComponent,
        HasAnyAuthorityDirective,
        ExtensionPointDirective,
        SecuredImageComponent,
        DeleteButtonDirective,
        DeleteDialogComponent,
        ResizeableContainerComponent,
        SecureLinkDirective,
        JhiConnectionStatusComponent,
        JhiConnectionWarningComponent,
        OrganizationSelectorComponent,
        CustomMinDirective,
        CustomMaxDirective,
        CustomPatternValidatorDirective,
        ItemCountComponent,
        ConsistencyCheckComponent,
        AssessmentWarningComponent,
    ],
})
export class ArtemisSharedModule {}
