import { Component } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-test-run-ribbon',
    template: `
        <div class="box">
            <div class="ribbon ribbon-top-left">
                <span jhiTranslate="artemisApp.examManagement.testRun.testRun"></span>
            </div>
        </div>
    `,
    styleUrls: ['../../../core/layouts/profiles/page-ribbon.scss'],
    imports: [TranslateDirective],
})
export class TestRunRibbonComponent {}
