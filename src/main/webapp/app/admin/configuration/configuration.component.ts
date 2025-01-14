import { Component, OnInit, inject } from '@angular/core';

import { ConfigurationService } from './configuration.service';
import { Bean, PropertySource } from './configuration.model';
import { faSort } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { JsonPipe, KeyValuePipe } from '@angular/common';

@Component({
    selector: 'jhi-configuration',
    templateUrl: './configuration.component.html',
    imports: [TranslateDirective, FormsModule, SortDirective, SortByDirective, FaIconComponent, JsonPipe, KeyValuePipe],
})
export class ConfigurationComponent implements OnInit {
    private configurationService = inject(ConfigurationService);

    allBeans!: Bean[];
    beans: Bean[] = [];
    beansFilter = '';
    beansAscending = true;
    propertySources: PropertySource[] = [];

    // Icons
    faSort = faSort;

    ngOnInit(): void {
        this.configurationService.getBeans().subscribe((beans) => {
            this.allBeans = beans;
            this.filterAndSortBeans();
        });

        this.configurationService.getPropertySources().subscribe((propertySources) => (this.propertySources = propertySources));
    }

    filterAndSortBeans(): void {
        const beansAscendingValue = this.beansAscending ? -1 : 1;
        const beansAscendingValueReverse = this.beansAscending ? 1 : -1;
        this.beans = this.allBeans
            .filter((bean) => !this.beansFilter || bean.prefix.toLowerCase().includes(this.beansFilter.toLowerCase()))
            .sort((a, b) => (a.prefix < b.prefix ? beansAscendingValue : beansAscendingValueReverse));
    }
}
