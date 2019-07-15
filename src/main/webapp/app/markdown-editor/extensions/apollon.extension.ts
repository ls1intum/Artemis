import { ApplicationRef, EmbeddedViewRef, ComponentFactoryResolver, Injector } from '@angular/core';
import * as showdown from 'showdown';
import { ApollonDiagram, ApollonDiagramService } from 'app/entities/apollon-diagram';
import { ModelingEditorComponent } from 'app/modeling-editor';

export const ApollonExtension = (componentFactoryResolver: ComponentFactoryResolver, appRef: ApplicationRef, injector: Injector, apollonService: ApollonDiagramService) => {
    return [
        {
            type: 'lang',
            filter: async (text: string, converter: showdown.Converter, options: showdown.ConverterOptions) => {
                const regex = /^\[apollon\](.*)\[\/apollon\]/;
                const match = regex.exec(text);
                if (match) {
                    const diagramId: number = Number(JSON.parse(match[1]));
                    return apollonService
                        .find(diagramId)
                        .map(({ body }) => body)
                        .toPromise()
                        .then((diagram: ApollonDiagram) => {
                            const componentRef = componentFactoryResolver.resolveComponentFactory(ModelingEditorComponent).create(injector);
                            componentRef.instance.readOnly = true;
                            componentRef.instance.umlModel = JSON.parse(diagram.jsonRepresentation);
                            appRef.attachView(componentRef.hostView);
                            return (componentRef.hostView as EmbeddedViewRef<any>).rootNodes[0] as HTMLElement;
                        });
                }
            },
        },
    ];
};
