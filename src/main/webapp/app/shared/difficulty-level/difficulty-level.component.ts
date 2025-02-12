import { Component, Input, OnDestroy, OnInit, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { DifficultyLevel } from 'app/entities/exercise.model';
import { Subscription } from 'rxjs';
import { CommonModule } from '@angular/common';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';

export interface ColoredDifficultyLevel {
    label: string;
    color: string[];
}
@Component({
    selector: 'jhi-difficulty-level',
    templateUrl: './difficulty-level.component.html',
    styleUrls: ['./difficulty-level.component.scss'],
    imports: [CommonModule, NgbTooltipModule],
})
export class DifficultyLevelComponent implements OnInit, OnDestroy {
    private translateService = inject(TranslateService);

    private translateSubscription: Subscription;
    @Input() difficultyLevel: string;
    coloredDifficultyLevel: ColoredDifficultyLevel = { label: '', color: [] };

    ngOnInit(): void {
        this.translateSubscription = this.translateService.onLangChange.subscribe(() => {
            this.mapDifficultyLevelToColors(this.difficultyLevel);
        });
        this.coloredDifficultyLevel = this.mapDifficultyLevelToColors(this.difficultyLevel);
    }

    mapDifficultyLevelToColors(difficultyLevel: string): ColoredDifficultyLevel {
        switch (difficultyLevel) {
            case DifficultyLevel.EASY:
                return { label: this.translateService.instant('artemisApp.exercise.easy'), color: [...Array(1).fill('success'), ...Array(2).fill('body')] };
            case DifficultyLevel.MEDIUM:
                return { label: this.translateService.instant('artemisApp.exercise.medium'), color: [...Array(2).fill('warning'), ...Array(1).fill('body')] };
            case DifficultyLevel.HARD:
                return { label: this.translateService.instant('artemisApp.exercise.hard'), color: [...Array(3).fill('danger')] };
        }
        return { label: this.translateService.instant('artemisApp.exercise.noLevel'), color: [...Array(3).fill('body')] };
    }

    ngOnDestroy(): void {
        this.translateSubscription?.unsubscribe();
    }
}
