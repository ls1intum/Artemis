import { Directive, DoCheck, ElementRef, Inject, Injector, Input, OnChanges, OnDestroy, OnInit, SimpleChanges } from '@angular/core';
import { UpgradeComponent } from '@angular/upgrade/static';
import { Participation } from '../entities/participation';
import { RepositoryService } from '../entities/repository';
import * as $ from 'jquery';
import 'angular';

/**
 * @directive editor
 * @selector editor
 * This Angular directive will act as an interface to the 'upgraded' AngularJS component
 *  The upgrade is realized as given Angular tutorial:
 *  https://angular.io/guide/upgrade#using-angularjs-component-directives-from-angular-code */
/* tslint:disable-next-line:directive-selector */
@Directive({selector: 'editor'})
/**
 * @class EditorComponentWrapper
 * @desc This directive is defined along the guidelines provided by the Angular Dev Team for upgrading components.
 * For this to work, the directive needs extend the "UpgradeComponent" and do a super call with the component's name
 * which has to be upgraded (here: editor) as argument.
 * Additionally, in order to support AOT compilation, we need to map functions like OnInit manually to the super class.
 *
 */
/* tslint:disable-next-line:directive-class-suffix */
export class EditorComponentWrapper extends UpgradeComponent implements OnInit, OnChanges, DoCheck, OnDestroy {
    /** The names of the input and output properties here must match the names of the
     *  `<` and `&` bindings in the AngularJS component that is being wrapped */

    /**
     * Injecting the translated dependencies as defined in the Editor component
     *  participation: '<',
     *  repository: '<',
     *  file: '<',
     */

    @Input() participation: Participation;
    @Input() repository: RepositoryService;
    @Input() file;
    @Input() repositoryFiles;

    /**
     * @constructor EditorComponentWrapper
     * @param {ElementRef} elementRef
     * @param {Injector} injector
     */
    constructor(@Inject(ElementRef) elementRef: ElementRef, @Inject(Injector) injector: Injector) {
        /** We must pass the name of the directive as used by AngularJS (!) to the super */
        super('editor', elementRef, injector);
    }

    /** For this class to work when compiled with AoT, we must implement these lifecycle hooks
     *  because the AoT compiler will not realise that the super class implements them */

    /**
     * @function ngOnInit
     */
    ngOnInit() { super.ngOnInit(); }

    /**
     * @function ngOnChanges
     */
    ngOnChanges(changes: SimpleChanges) { super.ngOnChanges(changes); }

    /**
     * @function ngDoCheck
     */
    ngDoCheck() { super.ngDoCheck(); }

    /**
     * @function ngOnDestroy
     */
    ngOnDestroy() { super.ngOnDestroy(); }
}

declare const angular: any;

/**
 * @class EditorController
 * Ported the definition and the function implementations for the AngularJS module here
 *  to prevent some routing errors and circumvent the separation of scopes */
class EditorController {
    static $inject = ['Participation', 'Repository'];

    /** Controller variables */
    isSaved = true;
    isBuilding = false;
    repository;
    isCommitted: boolean;
    latestResult = null;
    participation;
    saveStatusLabel;
    repositoryFiles: string[];

    init() {
        this.repository.isClean(this.participation.id).subscribe(res => {
            this.isCommitted = res.isClean;
        });
    }

    $onChanges(changes) {
        if (this.participation && this.repositoryFiles) {
            this.init();
        }
    }

    /** Collapse parts of the editor (file browser, build output...) */
    toggleCollapse = function($event, horizontal) {

        const target = $event.toElement || $event.relatedTarget || $event.target;

        target.blur();

        const $card = $(target).closest('.card');

        if ($card.hasClass('collapsed')) {
            $card.removeClass('collapsed');
        } else {
            $card.addClass('collapsed');
            if (horizontal) {
                $card.height('35px');
            } else {
                $card.width('55px');
            }

        }

    };

    /**
     *  This will not work as long as the bower component angular-resizeable is not used by the editor
     *  At the moment no events are fired as the css code was just copied to editor.scss file
     */
    /* $scope.$on('angular-resizable.resizeEnd', function ($event, args) {
        var $panel = $('#' + args.id);
        $panel.removeClass('collapsed');
    });*/

    updateSaveStatusLabel = function($event) {
        this.isSaved = $event.isSaved;
        if (!this.isSaved) {
            this.isCommitted = false;
        }
        this.saveStatusLabel = $event.saveStatusLabel;
    };

    updateLatestResult = function($event) {
        this.isBuilding = false;
        this.latestResult = $event.newResult;
    };

    commit = function($event) {

        const target = $event.toElement || $event.relatedTarget || $event.target;

        target.blur();
        this.isBuilding = true;
        this.repository.commit(this.participation.id).subscribe(
            res => {
                this.isCommitted = true;
                console.log('Successfully committed');
            },
            err => {
                console.log('Error occured');
            });
    };

}

/**
 * Defining the angularJS module here to circumvent separation of scopes
 * The definition is identical to the one in the AngularJS application
 * The template has to be required instead of directly declaring it via templateUrl
 * as a workaround to the problem "loading directive templates asynchronously
 * is not supported" when using AOT compiling.
 */
angular
    .module('artemisApp')
    .component('editor', {
        bindings: {
            participation: '<',
            repository: '<',
            file: '<',
            repositoryFiles: '<',
        },
        template: require('../../ng1/editor/editor.html'),
        controller: EditorController
    });
