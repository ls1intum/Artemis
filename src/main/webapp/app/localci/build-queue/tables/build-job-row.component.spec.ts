import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { BuildJobRowComponent } from './build-job-row.component';
import { BuildJob } from 'app/localci/shared/entities/build-job.model';

describe('build job row', () => {
    beforeEach(() => TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: TranslateService, useClass: MockTranslateService }] }));
    it('renders course links and cancels without navigating the row', () => {
        const fixture = TestBed.createComponent(BuildJobRowComponent);
        fixture.componentRef.setInput('buildJob', { id: 'job', name: 'Build', courseId: 5, priority: 3 } as BuildJob);
        fixture.componentRef.setInput('showCourseId', true);
        fixture.componentRef.setInput('courseId', 5);
        const cancel = vi.fn();
        const navigate = vi.fn();
        fixture.componentInstance.cancelJob.subscribe(cancel);
        fixture.componentInstance.jobClick.subscribe(navigate);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('a.stretched-row-link')?.getAttribute('href')).toBe('/course-management/5/build-overview/job/job-details');
        fixture.nativeElement.querySelector('button').click();
        expect(cancel).toHaveBeenCalledWith('job');
        expect(navigate).not.toHaveBeenCalled();
        fixture.nativeElement.click();
        expect(navigate).toHaveBeenCalledWith('job');
        navigate.mockClear();
        fixture.nativeElement.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
        expect(navigate).toHaveBeenCalledWith('job');
        navigate.mockClear();
        fixture.nativeElement.querySelector('button').dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
        expect(navigate).not.toHaveBeenCalled();
    });
});
