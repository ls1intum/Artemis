/**
 * Vitest tests for JvmThreadsComponent.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';

import { Thread, ThreadState } from 'app/core/admin/metrics/metrics.model';
import { JvmThreadsComponent } from 'app/core/admin/metrics/blocks/jvm-threads/jvm-threads.component';
import { MetricsModalThreadsComponent } from 'app/core/admin/metrics/blocks/metrics-modal-threads/metrics-modal-threads.component';

describe('JvmThreadsComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: JvmThreadsComponent;
    let fixture: ComponentFixture<JvmThreadsComponent>;
    let modalService: NgbModal;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [JvmThreadsComponent],
            providers: [NgbModal],
        })
            .overrideTemplate(JvmThreadsComponent, '<button class="hand btn btn-primary btn-sm" (click)="open()">Expand</button>')
            .compileComponents();

        fixture = TestBed.createComponent(JvmThreadsComponent);
        comp = fixture.componentInstance;
        modalService = TestBed.inject(NgbModal);
    });

    it('should store threads and create statistic counts', async () => {
        const threads = [
            { threadState: ThreadState.Blocked },
            { threadState: ThreadState.TimedWaiting },
            { threadState: ThreadState.TimedWaiting },
            { threadState: ThreadState.Runnable },
            { threadState: ThreadState.Waiting },
            { threadState: ThreadState.Waiting },
            { threadState: ThreadState.Waiting },
        ] as Thread[];

        fixture.componentRef.setInput('threads', threads);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(comp.threads()).toEqual(threads);
        expect(comp.threadStats()).toEqual({
            all: 7,
            runnable: 1,
            timedWaiting: 2,
            waiting: 3,
            blocked: 1,
        });
    });

    it('should open modal when open is called', () => {
        const mockModalRef = { componentInstance: { threads: undefined } };
        const spy = vi.spyOn(modalService, 'open').mockReturnValue(mockModalRef as NgbModalRef);

        const threads = [{ threadState: ThreadState.Blocked }] as Thread[];
        fixture.componentRef.setInput('threads', threads);
        fixture.detectChanges();

        comp.open();

        expect(spy).toHaveBeenCalledOnce();
        expect(spy).toHaveBeenCalledWith(MetricsModalThreadsComponent, { size: 'xl' });
        expect(mockModalRef.componentInstance.threads).toEqual(threads);
    });
});
