import { Component } from '@angular/core';

@Component({
    selector: 'jhi-test-run-ribbon',
    template: `
        <div class="box">
            <div class="ribbon ribbon-top-left">
                <span jhiTranslate="artemisApp.examManagement.testRun.testRun"></span>
            </div>
        </div>
    `,
    styleUrls: ['../../../shared/layouts/profiles/page-ribbon.scss'],
})
export class TestRunRibbonComponent {}
