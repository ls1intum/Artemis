import { Component, OnInit } from '@angular/core';
import { BonusService } from 'app/grading-system/bonus/bonus.service';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { GradingScale } from 'app/entities/grading-scale.model';

enum BonusStrategyOptions {
    GRADES,
    POINTS,
}

enum BonusStrategyDiscreteness {
    DISCRETE,
    CONTINUOUS,
}

@Component({
    selector: 'jhi-bonus',
    templateUrl: './bonus.component.html',
    styleUrls: ['./bonus.component.scss'],
})
export class BonusComponent implements OnInit {
    readonly CALCULATION_PLUS = 1;
    readonly CALCULATION_MINUS = -1;

    readonly bonusStrategyOptions = [BonusStrategyOptions.GRADES, BonusStrategyOptions.POINTS].map((bonusStrategyOption) => ({
        value: bonusStrategyOption,
        labelKey: 'artemisApp.TODO: Ata.' + BonusStrategyOptions[bonusStrategyOption].toLowerCase(),
        btnClass: 'btn-secondary',
    }));

    readonly bonusStrategyDiscreteness = [BonusStrategyDiscreteness.DISCRETE, BonusStrategyDiscreteness.CONTINUOUS].map((bonusStrategyDiscreteness) => ({
        value: bonusStrategyDiscreteness,
        labelKey: 'artemisApp.TODO: Ata.' + BonusStrategyDiscreteness[bonusStrategyDiscreteness].toLowerCase(),
        btnClass: 'btn-secondary',
    }));

    readonly calculationSigns = [
        {
            value: this.CALCULATION_PLUS,
            labelKey: '+',
            btnClass: 'btn-secondary',
        },
        {
            value: this.CALCULATION_MINUS,
            labelKey: '−',
            btnClass: 'btn-secondary',
        },
    ];

    sourceGradingScales: GradingScale[] = [];

    constructor(private bonusService: BonusService, private gradingSystemService: GradingSystemService) {}

    ngOnInit(): void {}
}
