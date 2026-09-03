import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { BuildContainersDetailsComponent } from './build-containers-details.component';
import { BuildPhasesEditorComponent } from 'app/programming/manage/build-plan-editor/build-phases-editor/build-phases-editor.component';
import { BuildPhaseEditorComponent } from 'app/programming/manage/build-plan-editor/build-phases-editor/build-phase/build-phase-editor.component';
import { BuildContainer } from 'app/programming/shared/entities/build-plan-phases.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('BuildContainersDetailsComponent', () => {
    let fixture: ComponentFixture<BuildContainersDetailsComponent>;

    const containers: BuildContainer[] = [
        {
            name: 'instructor_tests',
            dockerImage: 'image-a:1',
            repositories: [{ type: 'USER' }, { type: 'TESTS' }],
            phases: [
                { name: 'compile', script: 'echo compile', condition: 'ALWAYS', forceRun: false, resultPaths: [] },
                { name: 'test', script: 'echo test', condition: 'ALWAYS', forceRun: false, resultPaths: ['**/results/*.xml'] },
            ],
        },
        {
            name: 'student_tests',
            phases: [{ name: 'check', script: 'echo check', condition: 'ALWAYS', forceRun: false, resultPaths: [] }],
        },
    ];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [BuildContainersDetailsComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .overrideComponent(BuildContainersDetailsComponent, {
                remove: { imports: [TranslateDirective, ArtemisTranslatePipe] },
                add: { imports: [MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe, (key: string) => key)] },
            })
            // the real phases editor stays, so that its read-only mode can be observed; only the single phases are mocked
            .overrideComponent(BuildPhasesEditorComponent, {
                remove: { imports: [BuildPhaseEditorComponent, TranslateDirective] },
                add: { imports: [MockComponent(BuildPhaseEditorComponent), MockDirective(TranslateDirective)] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(BuildContainersDetailsComponent);
        fixture.componentRef.setInput('containers', containers);
        fixture.detectChanges();
    });

    const getContainerCards = () => fixture.debugElement.queryAll(By.css('.build-container-details'));

    it('should render one card per container with its name', () => {
        const cards = getContainerCards();
        expect(cards).toHaveLength(2);
        expect(cards.map((card) => card.query(By.css('.build-container-details-name')).nativeElement.textContent.trim())).toEqual(['instructor_tests', 'student_tests']);
    });

    it('should show the phases of each container in a read-only phases editor', () => {
        const cards = getContainerCards();

        const phaseEditors = cards.map((card) => card.queryAll(By.directive(BuildPhaseEditorComponent)));
        expect(phaseEditors.map((editors) => editors.length)).toEqual([2, 1]);
        // the add-phase button of a read-only phases editor is hidden
        const addPhaseButtons = fixture.debugElement.queryAll(By.css('#add-phase-button'));
        expect(addPhaseButtons).toHaveLength(2);
        expect(addPhaseButtons.every((button) => (button.nativeElement as HTMLButtonElement).hidden)).toBe(true);
    });

    it('should show the docker image, or that the container follows the language default', () => {
        const images = getContainerCards().map((card) => card.query(By.css('.build-container-details-image')));
        expect(images[0].nativeElement.textContent.trim()).toBe('image-a:1');
        // the second container has no image of its own: a translated placeholder text is shown instead
        expect(images[1].nativeElement.tagName).toBe('SPAN');
        expect(images[1].nativeElement.textContent.trim()).not.toBe('image-a:1');
    });

    it('should list the scoped repositories, or that the container checks out the repositories of the exercise', () => {
        const repositories = getContainerCards().map((card) => card.queryAll(By.css('.build-container-details-repository')));
        expect(repositories[0].map((repository) => repository.nativeElement.textContent.trim())).toEqual([
            'artemisApp.programmingExercise.buildContainersEditor.repositoryTypes.USER',
            'artemisApp.programmingExercise.buildContainersEditor.repositoryTypes.TESTS',
        ]);
        // an unscoped container shows the single "repositories of the exercise" text instead of badges
        expect(repositories[1]).toHaveLength(1);
        expect(repositories[1][0].nativeElement.classList.contains('badge')).toBe(false);
    });
});
