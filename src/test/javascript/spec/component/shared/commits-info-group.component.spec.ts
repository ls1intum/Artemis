import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CommitsInfoGroupComponent } from 'app/programming/shared/commits-info/commits-info-group/commits-info-group.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import dayjs from 'dayjs/esm';
import type { CommitInfo } from 'app/entities/programming/programming-submission.model';
import { CommitsInfoRowComponent } from 'app/programming/shared/commits-info/commits-info-group/commits-info-row/commits-info-row.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { TruncatePipe } from 'app/shared/pipes/truncate.pipe';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('CommitsInfoGroupComponent', () => {
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
            imports: [NgbTooltipModule],
            declarations: [CommitsInfoGroupComponent, CommitsInfoRowComponent, MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisDatePipe), MockPipe(TruncatePipe)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        });
        fixture = TestBed.createComponent(CommitsInfoGroupComponent);
        component = fixture.componentInstance;
        component.commits = [commitInfo1, commitInfo2];
        component.currentSubmissionHash = '456';
        component.previousSubmissionHash = '123';
        component.exerciseProjectKey = 'exerciseProjectKey';
        component.isRepositoryView = false;
        component.groupCount = 1;
        component.groupIndex = 0;
        component.pushNumber = 0;

        fixture.detectChanges();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should toggle isExpanded when clicking the expand button', () => {
        const compiled = fixture.nativeElement;
        let expandButton = compiled.querySelector('button');

        expect(component.getIsExpanded()).toBeFalse();

        expandButton.click();
        fixture.detectChanges();
        expect(component.getIsExpanded()).toBeTrue();

        expandButton = compiled.querySelector('button');

        expandButton.click();
        fixture.detectChanges();
        expect(component.getIsExpanded()).toBeFalse();
    });
});
