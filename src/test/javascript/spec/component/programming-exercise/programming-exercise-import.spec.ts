import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ProgrammingExercise, ProgrammingExerciseImportComponent, ProgrammingExercisePagingService, ProgrammingLanguage } from 'app/entities/programming-exercise';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import { Course } from 'app/entities/course';
import { SearchResult } from 'app/components/table';
import { TranslateModule } from '@ngx-translate/core';
import { ArtemisTestModule } from '../../test.module';
import { MockProgrammingExercisePagingService } from '../../mocks';
import { BrowserDynamicTestingModule } from '@angular/platform-browser-dynamic/testing';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { SinonStub, stub } from 'sinon';
import { Subject } from 'rxjs';
import { ArtemisSharedCommonModule } from 'app/shared';
import { SortByModule } from 'app/components/pipes';
import { DifferencePipe } from 'ngx-moment';

chai.use(sinonChai);
const expect = chai.expect;

describe('ProgrammingExerciseImportComponent', () => {
    let comp: ProgrammingExerciseImportComponent;
    let fixture: ComponentFixture<ProgrammingExerciseImportComponent>;
    let debugElement: DebugElement;
    let pagingService: ProgrammingExercisePagingService;

    let pagingStub: SinonStub;

    const basicCourse = { id: 12, title: 'Random course title' } as Course;
    const exercise = { id: 42, title: 'Exercise title', programmingLanguage: ProgrammingLanguage.JAVA, course: basicCourse } as ProgrammingExercise;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisSharedCommonModule, SortByModule],
            declarations: [ProgrammingExerciseImportComponent],
            providers: [DifferencePipe, { provide: ProgrammingExercisePagingService, useClass: MockProgrammingExercisePagingService }],
        })
            .overrideModule(BrowserDynamicTestingModule, { set: { entryComponents: [FaIconComponent] } })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseImportComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                pagingService = debugElement.injector.get(ProgrammingExercisePagingService);
                pagingStub = stub(pagingService, 'searchForExercises');
            });
    });

    afterEach(() => {
        pagingStub.restore();
    });

    it('should parse the pageable search result into the correct state', fakeAsync(() => {
        const searchResult = { resultsOnPage: [exercise], numberOfPages: 3 } as SearchResult<ProgrammingExercise>;
        const searchObservable = new Subject<SearchResult<ProgrammingExercise>>();
        pagingStub.returns(searchObservable);

        fixture.detectChanges();
        tick();

        expect(pagingStub).to.have.been.calledOnce;
        searchObservable.next(searchResult);

        fixture.detectChanges();
        tick();

        expect(comp.content.numberOfPages).to.be.eq(3);
        expect(comp.content.resultsOnPage[0].id).to.be.eq(42);
        expect(comp.total).to.be.eq(30);
        expect(comp.loading).to.be.false;
    }));
});
