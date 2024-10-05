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

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockComponent(NgbProgressbar)],
            declarations: [JvmThreadsComponent],
            providers: [{ provide: NgbModal, useClass: MockNgbModalService }],
        }).compileComponents();

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

    it('should open modal when expand is clicked', () => {
        const mockModalRef = { componentInstance: { threads: undefined } };
        const spy = jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as NgbModalRef);

        const threads = [{ threadState: ThreadState.Blocked }] as Thread[];
        fixture.componentRef.setInput('threads', threads);
        fixture.detectChanges();

        const button = fixture.debugElement.query(By.css('button.hand.btn.btn-primary.btn-sm'));
        expect(button).not.toBeNull();

        button.nativeElement.click();

        expect(spy).toHaveBeenCalledOnce();
        expect(spy).toHaveBeenCalledWith(MetricsModalThreadsComponent, { size: 'xl' });
        expect(mockModalRef.componentInstance.threads).toEqual(threads);
    });
});
