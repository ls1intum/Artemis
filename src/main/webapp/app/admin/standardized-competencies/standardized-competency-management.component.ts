import { Component } from '@angular/core';
import { NestedTreeControl } from '@angular/cdk/tree';
import { MatTreeNestedDataSource } from '@angular/material/tree';

@Component({
    selector: 'jhi-standardized-competency-management',
    templateUrl: './standardized-competency-management.component.html',
    styleUrls: ['standardized-competency-management.component.scss'],
})
export class StandardizedCompetencyManagementComponent {
    //TODO: add a debounce to the search
    title: string;
    knowledgeArea: string;
    treeControl = new NestedTreeControl<FoodNode>((node) => node.children);
    dataSource = new MatTreeNestedDataSource<FoodNode>();

    constructor() {
        this.dataSource.data = TREE_DATA;
    }

    hasChild = (_: number, node: FoodNode) => !!node.children && node.children.length > 0;
}

/**
 * Food data with nested structure.
 * Each node has a name and an optional list of children.
 */
interface FoodNode {
    name: string;
    children?: FoodNode[];
}

const TREE_DATA: FoodNode[] = [
    {
        name: 'Fruit',
        children: [{ name: 'Apple' }, { name: 'Banana' }, { name: 'Fruit loops' }],
    },
    {
        name: 'Vegetables',
        children: [
            {
                name: 'Green',
                children: [{ name: 'Broccoli' }, { name: 'Brussels sprouts' }],
            },
            {
                name: 'Orange',
                children: [{ name: 'Pumpkins' }, { name: 'Carrots' }],
            },
        ],
    },
];
