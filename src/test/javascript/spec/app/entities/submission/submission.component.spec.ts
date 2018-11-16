/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { SubmissionComponent } from 'app/entities/submission/submission.component';
import { SubmissionService } from 'app/entities/submission/submission.service';
import { Submission } from 'app/shared/model/submission.model';

describe('Component Tests', () => {
    describe('Submission Management Component', () => {
        let comp: SubmissionComponent;
        let fixture: ComponentFixture<SubmissionComponent>;
        let service: SubmissionService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [SubmissionComponent],
                providers: []
            })
                .overrideTemplate(SubmissionComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(SubmissionComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(SubmissionService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new Submission(123)],
                        headers
                    })
                )
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.submissions[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});
