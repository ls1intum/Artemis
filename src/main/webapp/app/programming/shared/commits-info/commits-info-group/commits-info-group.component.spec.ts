import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CommitsInfoGroupComponent } from 'app/programming/shared/commits-info/commits-info-group/commits-info-group.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import dayjs from 'dayjs/esm';
import type { CommitInfo } from 'app/programming/shared/entities/programming-submission.model';
import { CommitsInfoRowComponent } from 'app/programming/shared/commits-info/commits-info-group/commits-info-row/commits-info-row.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { TruncatePipe } from 'app/shared/pipes/truncate.pipe';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('CommitsInfoGroupComponent', () => {
    setupTestBed({ zoneless: true });

    let component: CommitsInfoGroupComponent;
    let fixture: ComponentFixture<CommitsInfoGroupComponent>;

    const commitInfo1 = {
        hash: '123',
        author: 'author',
        timestamp: dayjs('2021-01-01'),
        message: 'commit message',
    } as CommitInfo;
    const commitInfo2 = {
        hash: '456',
        author: 'author',
        timestamp: dayjs('2021-01-02'),
        message: 'other message',
    } as CommitInfo;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [NgbTooltipModule, CommitsInfoGroupComponent, CommitsInfoRowComponent, MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisDatePipe), MockPipe(TruncatePipe)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        });
        fixture = TestBed.createComponent(CommitsInfoGroupComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('commits', [commitInfo1, commitInfo2]);
        fixture.componentRef.setInput('currentSubmissionHash', '456');
        fixture.componentRef.setInput('previousSubmissionHash', '123');
        fixture.componentRef.setInput('exerciseProjectKey', 'exerciseProjectKey');
        fixture.componentRef.setInput('isRepositoryView', false);
        fixture.componentRef.setInput('groupCount', 1);
        fixture.componentRef.setInput('groupIndex', 0);
        fixture.componentRef.setInput('pushNumber', 0);

        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should toggle isExpanded when clicking the expand button', () => {
        const compiled = fixture.nativeElement;
        let expandButton = compiled.querySelector('button');

        expect(component.getIsExpanded()).toBe(false);

        expandButton.click();
        fixture.detectChanges();
        expect(component.getIsExpanded()).toBe(true);

        expandButton = compiled.querySelector('button');

        expandButton.click();
        fixture.detectChanges();
        expect(component.getIsExpanded()).toBe(false);
    });
});
