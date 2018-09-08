/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { ResultDetailComponent } from 'app/entities/result/result-detail.component';
import { Result } from 'app/shared/model/result.model';

describe('Component Tests', () => {
    describe('Result Management Detail Component', () => {
        let comp: ResultDetailComponent;
        let fixture: ComponentFixture<ResultDetailComponent>;
        const route = ({ data: of({ result: new Result(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ResultDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }]
            })
                .overrideTemplate(ResultDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(ResultDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.result).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
