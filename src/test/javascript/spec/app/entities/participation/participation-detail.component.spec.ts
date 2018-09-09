/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArTEMiSTestModule } from '../../../test.module';
import { ParticipationDetailComponent } from 'app/entities/participation/participation-detail.component';
import { Participation } from 'app/shared/model/participation.model';

describe('Component Tests', () => {
    describe('Participation Management Detail Component', () => {
        let comp: ParticipationDetailComponent;
        let fixture: ComponentFixture<ParticipationDetailComponent>;
        const route = ({ data: of({ participation: new Participation(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [ParticipationDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }]
            })
                .overrideTemplate(ParticipationDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(ParticipationDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.participation).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
