import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TaskCountWarningComponent } from 'app/programming/manage/instructions-editor/analysis/task-count-warning/task-count-warning.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('TaskCountWarningComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<TaskCountWarningComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        });

        fixture = TestBed.createComponent(TaskCountWarningComponent);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    //query element by id
    it('should correctly compare the number of tasks', () => {
        fixture.componentRef.setInput('numOfTasks', 5);
        fixture.componentRef.setInput('advisedMaxNumOfTasks', 15);
        fixture.detectChanges();

        const okayInformation = fixture.nativeElement.querySelector('#instruction_analysis_num-of-tasks-ok');
        expect(okayInformation).toBeDefined();

        const issueInformation = fixture.nativeElement.querySelector('#instruction_analysis_num-of-tasks-issues');
        expect(issueInformation).toBeNull();
    });
});
