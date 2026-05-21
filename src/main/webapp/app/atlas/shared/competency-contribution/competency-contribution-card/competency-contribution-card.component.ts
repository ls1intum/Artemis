import { DecimalPipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FaIconComponent, FaLayersComponent } from '@fortawesome/angular-fontawesome';
import { faArrowRight } from '@fortawesome/free-solid-svg-icons';
import { NgbProgressbar, NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { HIGH_COMPETENCY_LINK_WEIGHT, MEDIUM_COMPETENCY_LINK_WEIGHT } from 'app/atlas/shared/entities/competency.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-competency-contribution-card',
    imports: [DecimalPipe, FaIconComponent, FaLayersComponent, NgbTooltipModule, NgbProgressbar, TranslateDirective, ArtemisTranslatePipe, RouterLink],
    templateUrl: './competency-contribution-card.component.html',
})
export class CompetencyContributionCardComponent {
    courseId = input.required<number>();
    competencyId = input.required<number>();
    title = input.required<string>();
    weight = input.required<number>();
    mastery = input<number>();

    protected readonly HIGH_COMPETENCY_LINK_WEIGHT = HIGH_COMPETENCY_LINK_WEIGHT;
    protected readonly MEDIUM_COMPETENCY_LINK_WEIGHT = MEDIUM_COMPETENCY_LINK_WEIGHT;

    protected readonly faArrowRight = faArrowRight;
}
