import { Component } from '@angular/core';
import { NestedTreeControl } from '@angular/cdk/tree';
import { MatTreeNestedDataSource } from '@angular/material/tree';
import { faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { Competency, CompetencyTaxonomy } from 'app/entities/competency.model';

@Component({
    selector: 'jhi-standardized-competency-management',
    templateUrl: './standardized-competency-management.component.html',
    styleUrls: ['standardized-competency-management.component.scss'],
})
export class StandardizedCompetencyManagementComponent {
    //TODO: add a debounce to the search
    title: string;
    knowledgeAreaFilter?: KnowledgeArea;
    knowledgeAreaArray: KnowledgeArea[];
    protected readonly faChevronRight = faChevronRight;

    readonly trackBy = (_: number, node: KnowledgeArea) => node.id;

    constructor() {
        this.dataSourceNested.data = DATA_2;
        this.knowledgeAreaArray = DATA_2.flatMap((knowledgeArea) => this.getSelfAndChildrenAsArray(knowledgeArea));
    }

    treeControlNested = new NestedTreeControl<KnowledgeArea>((node) => node.children);
    dataSourceNested = new MatTreeNestedDataSource<KnowledgeArea>();

    filterByKnowledgeArea() {
        if (this.knowledgeAreaFilter == undefined || this.knowledgeAreaFilter.id == undefined) {
            this.dataSourceNested.data = DATA_2;
        } else {
            const foundKa: KnowledgeArea = this.knowledgeAreaArray.find((ka) => ka.id == this.knowledgeAreaFilter!.id)!;
            this.dataSourceNested.data = [foundKa];
            this.treeControlNested.expand(foundKa);
        }
    }

    filterByCompetencyName() {
        //TODO: if competency name is empty do nothing
        //TODO: if competency name is not empty ->
    }

    refreshTree() {
        const _data = this.dataSourceNested.data;
        this.dataSourceNested.data = [];
        this.dataSourceNested.data = _data;
    }

    getSelfAndChildrenAsArray(knowledgeArea: KnowledgeArea): KnowledgeArea[] {
        if (knowledgeArea.children) {
            const childrenTitles = knowledgeArea.children.map((child) => this.getSelfAndChildrenAsArray(child)).flat();
            return [knowledgeArea, ...childrenTitles];
        }
        return [knowledgeArea];
    }

    minimize(knowledgeArea: KnowledgeArea): KnowledgeArea {
        return {
            id: knowledgeArea.id,
            title: knowledgeArea.title,
        };
    }
}

const DATA_2: KnowledgeArea[] = [
    {
        id: 1,
        title: 'KA1',
        description: 'KADESC1',
        children: [
            {
                id: 2,
                title: 'KA2',
                description: 'KADESC2',
                competencies: [
                    {
                        id: 3,
                        title: 'COMP3',
                        description: 'COMPDESC3',
                        taxonomy: CompetencyTaxonomy.ANALYZE,
                    },
                ],
            },
            {
                id: 3,
                title: 'KA3',
                description: 'KADESC3',
                children: [
                    {
                        id: 4,
                        title: 'KA4',
                        description: 'KADESC4',
                    },
                ],
                competencies: [
                    {
                        id: 5,
                        title: 'COMP5',
                        description: 'COMPDESC5',
                        taxonomy: CompetencyTaxonomy.ANALYZE,
                    },
                ],
            },
        ],
        competencies: [
            {
                id: 1,
                title: 'COMP1',
                description: 'COMPDESC1',
                taxonomy: CompetencyTaxonomy.ANALYZE,
            },
            {
                id: 2,
                title: 'COMP2',
                description: 'COMPDESC2',
                taxonomy: CompetencyTaxonomy.UNDERSTAND,
            },
        ],
    },
    {
        id: 9,
        title: 'KA9',
        description: 'KADESC9',
        competencies: [
            {
                id: 8,
                title: 'COMP8',
                description: 'COMPDESC8',
                taxonomy: CompetencyTaxonomy.ANALYZE,
            },
            {
                id: 9,
                title: 'COMP9',
                description: 'COMPDESC9',
                taxonomy: CompetencyTaxonomy.UNDERSTAND,
            },
        ],
    },
];

interface KnowledgeArea {
    id?: number;
    title?: string;
    description?: string;
    parent?: KnowledgeArea;
    children?: KnowledgeArea[];
    competencies?: StandardizedCompetency[];
}

interface StandardizedCompetency {
    id?: number;
    title?: string;
    description?: string;
    taxonomy?: CompetencyTaxonomy;
    version?: string;
    knowledgeArea?: KnowledgeArea;
    source?: Source;
    firstVersion?: StandardizedCompetency;
    childVersions?: StandardizedCompetency[];
    linkedCompetencies?: Competency[];
}

interface Source {
    title?: string;
    author?: string;
    uri?: string;
    competencies?: StandardizedCompetency[];
}
