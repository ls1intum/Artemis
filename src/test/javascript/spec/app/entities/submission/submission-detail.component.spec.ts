/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

import { ArTeMiSTestModule } from '../../../test.module';
import { SubmissionDetailComponent } from '../../../../../../main/webapp/app/entities/submission/submission-detail.component';
import { SubmissionService } from '../../../../../../main/webapp/app/entities/submission/submission.service';
import { Submission } from '../../../../../../main/webapp/app/entities/submission/submission.model';

describe('Component Tests', () => {

    describe('Submission Management Detail Component', () => {
        let comp: SubmissionDetailComponent;
        let fixture: ComponentFixture<SubmissionDetailComponent>;
        let service: SubmissionService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [SubmissionDetailComponent],
                providers: [
                    SubmissionService
                ]
            })
            .overrideTemplate(SubmissionDetailComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(SubmissionDetailComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(SubmissionService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                spyOn(service, 'find').and.returnValue(Observable.of(new HttpResponse({
                    body: new Submission(123)
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.find).toHaveBeenCalledWith(123);
                expect(comp.submission).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
