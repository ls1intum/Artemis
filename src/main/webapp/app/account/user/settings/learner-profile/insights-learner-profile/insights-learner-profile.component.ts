import { Component } from '@angular/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { IrisLearnerProfileComponent } from 'app/account/user/settings/learner-profile/insights-learner-profile/iris-learner-profile/iris-learner-profile.component';

@Component({
    selector: 'jhi-insights-learner-profile',
    imports: [TranslateDirective, IrisLearnerProfileComponent],
    templateUrl: './insights-learner-profile.component.html',
})
export class InsightsLearnerProfileComponent {}
