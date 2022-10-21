import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ArtemisTestModule } from '../../../test.module';
import { Thread, ThreadState } from 'app/admin/metrics/metrics.model';
import { JvmThreadsComponent } from 'app/admin/metrics/blocks/jvm-threads/jvm-threads.component';
import { MetricsModalThreadsComponent } from 'app/admin/metrics/blocks/metrics-modal-threads/metrics-modal-threads.component';
import { By } from '@angular/platform-browser';
import { MockNgbModalService } from '../../../helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal, NgbModalRef, NgbProgressbar } from '@ng-bootstrap/ng-bootstrap';
import { MockComponent } from 'ng-mocks';

describe('JvmThreadsComponent', () => {
    let comp: JvmThreadsComponent;
    let fixture: ComponentFixture<JvmThreadsComponent>;
    let modalService: NgbModal;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [JvmThreadsComponent, MockComponent(NgbProgressbar)],
            providers: [{ provide: NgbModal, useClass: MockNgbModalService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(JvmThreadsComponent);
                comp = fixture.componentInstance;
                modalService = TestBed.inject(NgbModal);
            });
    });

    it('should store threads and create statistic counts', () => {
        const threads = [
            { threadState: ThreadState.Blocked },
            { threadState: ThreadState.TimedWaiting },
            { threadState: ThreadState.TimedWaiting },
            { threadState: ThreadState.Runnable },
            { threadState: ThreadState.Waiting },
            { threadState: ThreadState.Waiting },
            { threadState: ThreadState.Waiting },
        ] as Thread[];

        comp.threads = threads;

        expect(comp.threads).toEqual(threads);
        expect(comp.threadStats).toEqual({
            threadDumpAll: 7,
            threadDumpRunnable: 1,
            threadDumpTimedWaiting: 2,
            threadDumpWaiting: 3,
            threadDumpBlocked: 1,
        });
    });

    it('should open modal when expand is clicked', () => {
        const mockModalRef = { componentInstance: { threads: undefined } };
        const spy = jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as NgbModalRef);

        const threads = [{ threadState: ThreadState.Blocked }] as Thread[];
        comp.threads = threads;
        fixture.detectChanges();

        const button = fixture.debugElement.query(By.css('button.hand.btn.btn-primary.btn-sm'));
        expect(button).not.toBeNull();

        button.nativeElement.click();

        expect(spy).toHaveBeenCalledOnce();
        expect(spy).toHaveBeenCalledWith(MetricsModalThreadsComponent, { size: 'xl' });
        expect(mockModalRef.componentInstance.threads).toEqual(threads);
    });
});
