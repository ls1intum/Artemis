import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { TextHintDetailComponent } from 'app/exercises/shared/exercise-hint/manage/exercise-hint-detail.component';
import { ArtemisTestModule } from '../../test.module';
import { TextHint } from 'app/entities/hestia/text-hint-model';

describe('TextHint Management Detail Component', () => {
    let comp: TextHintDetailComponent;
    let fixture: ComponentFixture<TextHintDetailComponent>;
    const textHint = new TextHint();
    textHint.id = 123;
    const route = { data: of({ textHint }), params: of({ exerciseId: 1 }) } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [TextHintDetailComponent],
            providers: [{ provide: ActivatedRoute, useValue: route }],
        })
            .overrideTemplate(TextHintDetailComponent, '')
            .compileComponents();
        fixture = TestBed.createComponent(TextHintDetailComponent);
        comp = fixture.componentInstance;
    });

    describe('OnInit', () => {
        it('Should call load all on init', () => {
            // GIVEN

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(comp.textHint).toEqual(expect.objectContaining({ id: 123 }));
        });
    });
});
