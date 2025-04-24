import { inject } from '@angular/core';
import { Component, computed, input, output, signal } from '@angular/core';
import { IconDefinition, faDownload, faExternalLinkAlt, faSquare, faSquareCheck } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';

import { NgbCollapseModule, NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { LectureUnit } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';

@Component({
    selector: 'jhi-lecture-unit-card',
    imports: [FontAwesomeModule, NgbCollapseModule, TranslateDirective, ArtemisTranslatePipe, ArtemisDatePipe, CommonModule, NgbTooltipModule],
    templateUrl: './lecture-unit.component.html',
    styleUrl: './lecture-unit.component.scss',
})
export class LectureUnitComponent {
    private router = inject(Router);

    protected faDownload = faDownload;
    protected faSquareCheck = faSquareCheck;
    protected faSquare = faSquare;

    lectureUnit = input.required<LectureUnit>();
    icon = input.required<IconDefinition>();

    showViewIsolatedButton = input<boolean>(false);
    viewIsolatedButtonLabel = input<string>('artemisApp.textUnit.isolated');
    viewIsolatedButtonIcon = input<IconDefinition>(faExternalLinkAlt);
    isPresentationMode = input.required<boolean>();

    readonly showOriginalVersionButton = input<boolean>(false);
    readonly onShowOriginalVersion = output<void>();

    readonly onShowIsolated = output<void>();
    readonly onCollapse = output<boolean>();
    readonly onCompletion = output<boolean>();

    readonly isCollapsed = signal<boolean>(true);

    readonly isVisibleToStudents = computed(() => this.lectureUnit().visibleToStudents);
    readonly isStudentPath = computed(() => this.router.url.startsWith('/courses'));

    toggleCompletion(event: Event) {
        event.stopPropagation();
        this.onCompletion.emit(!this.lectureUnit().completed!);
    }

    toggleCollapse() {
        this.isCollapsed.update((isCollapsed) => !isCollapsed);
        this.onCollapse.emit(this.isCollapsed());
    }

    handleIsolatedView(event: Event) {
        event.stopPropagation();
        this.onShowIsolated.emit();
    }

    handleOriginalVersionView(event: Event) {
        event.stopPropagation();
        this.onShowOriginalVersion.emit();
    }
}
