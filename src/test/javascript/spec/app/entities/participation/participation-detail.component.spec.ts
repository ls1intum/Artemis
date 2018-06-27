/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

import { ArTEMiSTestModule } from '../../../test.module';
import { ParticipationDetailComponent } from '../../../../../../main/webapp/app/entities/participation/participation-detail.component';
import { ParticipationService } from '../../../../../../main/webapp/app/entities/participation/participation.service';
import { Participation } from '../../../../../../main/webapp/app/entities/participation/participation.model';

describe('Component Tests', () => {

    describe('Participation Management Detail Component', () => {
        let comp: ParticipationDetailComponent;
        let fixture: ComponentFixture<ParticipationDetailComponent>;
        let service: ParticipationService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [ParticipationDetailComponent],
                providers: [
                    ParticipationService
                ]
            })
            .overrideTemplate(ParticipationDetailComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(ParticipationDetailComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ParticipationService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                spyOn(service, 'find').and.returnValue(Observable.of(new HttpResponse({
                    body: new Participation(123)
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.find).toHaveBeenCalledWith(123);
                expect(comp.participation).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
