/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { ApollonDiagramDetailComponent } from 'app/entities/apollon-diagram/apollon-diagram-detail.component';
import { ApollonDiagram } from 'app/shared/model/apollon-diagram.model';

describe('Component Tests', () => {
    describe('ApollonDiagram Management Detail Component', () => {
        let comp: ApollonDiagramDetailComponent;
        let fixture: ComponentFixture<ApollonDiagramDetailComponent>;
        const route = ({ data: of({ apollonDiagram: new ApollonDiagram(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ApollonDiagramDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }]
            })
                .overrideTemplate(ApollonDiagramDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(ApollonDiagramDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.apollonDiagram).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
