import { Component } from '@angular/core';

@Component({
    selector: 'jhi-test-run-ribbon',
    template: `
        <div class="ribbon">
            <a href="" jhiTranslate="artemisApp.examManagement.testRun.testRun"></a>
        </div>
    `,
    styleUrls: ['../../../shared/layouts/profiles/page-ribbon.scss'],
})
export class TestRunRibbonComponent {}
