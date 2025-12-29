import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { GradingSystemComponent } from 'app/assessment/manage/grading-system/grading-system.component';
import { ActivatedRoute, Params } from '@angular/router';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { of } from 'rxjs';
import { GradeType } from 'app/assessment/shared/entities/grading-scale.model';
import { BaseGradingSystemComponent } from 'app/assessment/manage/grading-system/base-grading-system/base-grading-system.component';
import { DocumentationButtonComponent } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { GradingSystemInfoModalComponent } from 'app/assessment/manage/grading-system/grading-system-info-modal/grading-system-info-modal.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { GradingSystemPresentationsComponent } from 'app/assessment/manage/grading-system/grading-system-presentations/grading-system-presentations.component';

describe('GradingSystemComponent', () => {
    setupTestBed({ zoneless: true });
    let fixture: ComponentFixture<GradingSystemComponent>;
    let component: GradingSystemComponent;

    describe('with course context', () => {
        const courseId = 123;
        const route = {
            params: of({ courseId } as Params),
        } as ActivatedRoute;

        beforeEach(() => {
            return TestBed.configureTestingModule({
                imports: [GradingSystemComponent],
                providers: [
                    { provide: ActivatedRoute, useValue: route },
                    { provide: TranslateService, useClass: MockTranslateService },
                ],
            })
                .overrideComponent(GradingSystemComponent, {
                    remove: {
                        imports: [
                            DocumentationButtonComponent,
                            GradingSystemInfoModalComponent,
                            FaIconComponent,
                            TranslateDirective,
                            ArtemisTranslatePipe,
                            HelpIconComponent,
                            GradingSystemPresentationsComponent,
                        ],
                    },
                    add: {
                        imports: [
                            MockComponent(DocumentationButtonComponent),
                            MockComponent(GradingSystemInfoModalComponent),
                            MockComponent(FaIconComponent),
                            MockDirective(TranslateDirective),
                            MockPipe(ArtemisTranslatePipe),
                            MockComponent(HelpIconComponent),
                            MockComponent(GradingSystemPresentationsComponent),
                        ],
                    },
                })
                .compileComponents()
                .then(() => {
                    fixture = TestBed.createComponent(GradingSystemComponent);
                    component = fixture.componentInstance;
                });
        });

        it('should initialize with course context', () => {
            fixture.detectChanges();

            expect(component).toBeTruthy();
            expect(component.courseId).toBe(courseId);
            expect(component.examId).toBeUndefined();
            expect(component.isExam).toBe(false);
        });

        it('should expose GradeType enum', () => {
            expect(component.GradeType).toBe(GradeType);
        });

        it('should expose documentation type', () => {
            expect(component.documentationType).toBe('Grading');
        });

        it('should expose faExclamationTriangle icon', () => {
            expect(component.faExclamationTriangle).toBeDefined();
        });

        it('should capture child component on activation', () => {
            const mockChildComponent = {} as BaseGradingSystemComponent;

            component.onChildActivate(mockChildComponent);

            expect(component.childComponent).toBe(mockChildComponent);
        });
    });

    describe('with exam context', () => {
        const courseId = 456;
        const examId = 789;
        const route = {
            params: of({ courseId, examId } as Params),
        } as ActivatedRoute;

        beforeEach(() => {
            return TestBed.configureTestingModule({
                imports: [GradingSystemComponent],
                providers: [
                    { provide: ActivatedRoute, useValue: route },
                    { provide: TranslateService, useClass: MockTranslateService },
                ],
            })
                .overrideComponent(GradingSystemComponent, {
                    remove: {
                        imports: [
                            DocumentationButtonComponent,
                            GradingSystemInfoModalComponent,
                            FaIconComponent,
                            TranslateDirective,
                            ArtemisTranslatePipe,
                            HelpIconComponent,
                            GradingSystemPresentationsComponent,
                        ],
                    },
                    add: {
                        imports: [
                            MockComponent(DocumentationButtonComponent),
                            MockComponent(GradingSystemInfoModalComponent),
                            MockComponent(FaIconComponent),
                            MockDirective(TranslateDirective),
                            MockPipe(ArtemisTranslatePipe),
                            MockComponent(HelpIconComponent),
                            MockComponent(GradingSystemPresentationsComponent),
                        ],
                    },
                })
                .compileComponents()
                .then(() => {
                    fixture = TestBed.createComponent(GradingSystemComponent);
                    component = fixture.componentInstance;
                });
        });

        it('should initialize with exam context', () => {
            fixture.detectChanges();

            expect(component).toBeTruthy();
            expect(component.courseId).toBe(courseId);
            expect(component.examId).toBe(examId);
            expect(component.isExam).toBe(true);
        });
    });
});
