import { Component, ElementRef, OnChanges, OnDestroy, OnInit, SimpleChanges, computed, inject, input, output, viewChild } from '@angular/core';
import { LoadingIndicatorContainerComponent } from 'app/shared/loading-indicator-container/loading-indicator-container.component';
import { faArrowsToEye, faHexagonNodes, faMinus, faPlus } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import {
    MemirisConnectionType,
    MemirisGraphData,
    MemirisGraphSettings,
    MemirisLearningNode,
    MemirisMemoryNode,
    MemirisSimulationLink,
    MemirisSimulationLinkMemoryLearning,
    MemirisSimulationLinkMemoryMemory,
    MemirisSimulationNode,
} from 'app/iris/shared/entities/memiris.model';
import {
    D3DragEvent,
    D3ZoomEvent,
    DragBehavior,
    ForceLink,
    Selection,
    Simulation,
    SubjectPosition,
    ZoomBehavior,
    drag,
    forceCollide,
    forceLink,
    forceManyBody,
    forceSimulation,
    forceX,
    forceY,
    select,
    zoom,
    zoomIdentity,
} from 'd3';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateService } from '@ngx-translate/core';
import { Subscription } from 'rxjs';

/**
 * The MemirisGraphViewComponent is responsible for rendering and managing an interactive graph visualization.
 * It uses D3.js to display nodes and links that represent memories, learnings, and their connections.
 *
 * The component offers dynamic features such as zooming, panning, and node highlighting.
 * It responds to changes in its inputs, automatically updating the graph visualization when the data,
 * settings, or selected node are modified.
 *
 * Features:
 * - Displays nodes representing memories and learnings.
 * - Renders links showing relationships or connections between the nodes.
 * - Integrates filtering, allowing the graph to adapt based on provided settings.
 * - Supports zooming and panning for better navigation through the graph.
 * - Highlights nodes when they are selected.
 */
@Component({
    selector: 'jhi-memiris-graph-view',
    imports: [LoadingIndicatorContainerComponent, FontAwesomeModule, ButtonComponent, ArtemisTranslatePipe],
    templateUrl: './memiris-graph-view.component.html',
    styleUrl: './memiris-graph-view.component.scss',
})
export class MemirisGraphViewComponent implements OnInit, OnDestroy, OnChanges {
    // Inputs and Outputs
    graphData = input<MemirisGraphData>();
    initialSelectedMemoryId = input<string>();
    settings = input<MemirisGraphSettings>(new MemirisGraphSettings());
    nodeSelected = output<MemirisSimulationNode>();
    artemisTranslatePipe = inject(ArtemisTranslatePipe);
    translateService = inject(TranslateService);

    // Icons
    faPlus = faPlus;
    faMinus = faMinus;
    faArrowsToEye = faArrowsToEye;
    faHexagonNodes = faHexagonNodes;

    // Internal state
    svgContainer = viewChild<ElementRef>('svgContainer');
    loading = computed(() => !this.graphData());
    selectedNode?: MemirisSimulationNode;
    nodes: MemirisSimulationNode[] = [];
    links: MemirisSimulationLink[] = [];
    allNodes: MemirisSimulationNode[] = [];
    allLinks: MemirisSimulationLink[] = [];
    subscriptions: Subscription[] = [];

    // D3-related properties
    private simulation?: Simulation<MemirisSimulationNode, MemirisSimulationLink> = undefined;
    private svg?: Selection<SVGSVGElement, unknown, null, undefined> = undefined;
    private linkElements?: Selection<SVGLineElement, MemirisSimulationLink, SVGGElement, unknown> = undefined;
    private nodeElements?: Selection<SVGCircleElement, MemirisSimulationNode, SVGGElement, unknown> = undefined;
    private textElements?: Selection<SVGTextElement, MemirisSimulationNode, SVGGElement, unknown> = undefined;
    private linkLabelElements?: Selection<SVGTextElement, MemirisSimulationLink, SVGGElement, unknown> = undefined;
    private zoom?: ZoomBehavior<SVGSVGElement, unknown> = undefined;
    private width = 0;
    private height = 0;
    private graphGroup: Selection<SVGGElement, unknown, null, undefined>;

    // Define padding for collision detection
    private PADDING = 8;

    ngOnInit(): void {
        this.subscriptions.push(
            this.translateService.onLangChange.subscribe(() => {
                requestAnimationFrame(() => this.updateGraph());
            }),
        );
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes['settings']) {
            this.applySettings();
            requestAnimationFrame(() => this.updateGraph());
        }

        if (changes['selectedNode']) {
            this.highlightNode(this.selectedNode);
        }

        if (changes['graphData']) {
            const data = changes['graphData'].currentValue as MemirisGraphData;
            this.updateGraphData(data);
        }
    }

    /**
     * Ensures that the simulation is stopped when the component is destroyed.
     */
    ngOnDestroy(): void {
        if (this.simulation) {
            this.simulation.stop();
        }
        this.subscriptions.forEach((subscription) => subscription.unsubscribe());
    }

    /**
     * Updates the graph data by creating nodes and links based on the provided `MemirisGraphData`.
     * This method processes memories, learnings, and connections to populate the graph structure
     * and applies settings before then updating the graph visualization.
     *
     * @param {MemirisGraphData} data - The input data for the graph, containing memories, learnings, and connections.
     */
    private updateGraphData(data: MemirisGraphData) {
        if (!data) {
            return;
        }
        // Create nodes from graph data
        this.allNodes = data.memories.map((memory) => new MemirisMemoryNode(memory));
        this.allNodes.push(...data.learnings.map((learning) => new MemirisLearningNode(learning)));

        // Precompute Maps for fast lookups
        const learningMap = new Map(data.learnings.map((learning) => [learning.id, learning]));
        const nodeMap = new Map(this.allNodes.map((node) => [node.getId(), node]));

        // Create links from graph data
        this.allLinks = [];

        // Create memory-learning links
        data.memories.forEach((memory) => {
            const memoryNode = nodeMap.get(memory.id);
            if (!memoryNode) return;

            memory.learnings?.forEach((learningId) => {
                const learning = learningMap.get(learningId);
                const learningNode = nodeMap.get(learningId);
                if (learning && learningNode) {
                    this.allLinks.push(new MemirisSimulationLinkMemoryLearning(memoryNode as MemirisMemoryNode, learningNode as MemirisLearningNode));
                }
            });
        });

        // Create memory-memory links
        data.connections.forEach((connection) => {
            if (connection.memories && connection.memories.length >= 2) {
                // CREATED_FROM connections have a special handling
                // Only the first memory is the source, the rest are targets
                if (connection.connection_type === MemirisConnectionType.CREATED_FROM) {
                    const sourceMemory = this.allNodes.find((node) => node.getId() === connection.memories[0].id);
                    if (sourceMemory) {
                        connection.memories.slice(1).forEach((targetMemory) => {
                            const targetNode = this.allNodes.find((node) => node.getId() === targetMemory.id);
                            if (targetNode) {
                                this.allLinks.push(new MemirisSimulationLinkMemoryMemory(connection, sourceMemory as MemirisMemoryNode, targetNode as MemirisMemoryNode));
                            }
                        });
                    }
                }

                // Every other connection type creates a link for each pair of memories
                if (connection.connection_type !== MemirisConnectionType.CREATED_FROM) {
                    connection.memories.forEach((sourceMemory, index) => {
                        const sourceNode = this.allNodes.find((node) => node.getId() === sourceMemory.id);
                        if (!sourceNode) return;

                        // Create links to all other memories in the connection
                        connection.memories.forEach((targetMemory, targetIndex) => {
                            if (index === targetIndex) {
                                return;
                            }
                            const targetNode = this.allNodes.find((node) => node.getId() === targetMemory.id);
                            if (targetNode) {
                                this.allLinks.push(new MemirisSimulationLinkMemoryMemory(connection, sourceNode as MemirisMemoryNode, targetNode as MemirisMemoryNode));
                            }
                        });
                    });
                }
            }
        });

        this.applySettings();

        requestAnimationFrame(() => this.updateGraph());
    }

    /**
     * Initializes the D3 graph visualization by setting up the SVG container,
     * creating the simulation, and defining the forces acting on nodes and links.
     * This method is called when the component is initialized or when the graph data changes.
     * It sets up the zoom behavior, creates groups for links and nodes, and starts the simulation.
     * It also handles the initial rendering of nodes and links based on the provided data.
     */
    private initializeGraph(): void {
        const element = this.svgContainer()?.nativeElement;
        if (!element || !this.nodes.length) return;

        this.width = element.clientWidth || 800;
        this.height = element.clientHeight || 600;

        // Initialize nodes in a circular layout to prevent initial overlapping
        this.initializeCircularLayout();

        // Create SVG element
        this.svg = select(element).attr('width', '100%').attr('height', '100%').attr('viewBox', [0, 0, this.width, this.height]);

        // Clear any existing content
        this.svg.selectAll('*').remove();

        // Create container group for zoom
        this.graphGroup = this.svg.append('g');

        // Define arrow markers for links
        this.svg
            .append('defs')
            .append('marker')
            .attr('id', 'arrowhead')
            .attr('viewBox', '0 0 451.847 451.847')
            .attr('refX', 750) // Position the arrow away from the target node
            .attr('refY', 225.92)
            .attr('markerWidth', 10)
            .attr('markerHeight', 10)
            .attr('orient', 'auto')
            .append('path')
            .attr(
                'd',
                'M97.141,225.92c0-8.095,3.091-16.192,9.259-22.366L300.689,9.27c12.359-12.359,32.397-12.359,44.751,0 c12.354,12.354,12.354,32.388,0,44.748L173.525,225.92l171.903,171.909c12.354,12.354,12.354,32.391,0,44.744 c-12.354,12.365-32.386,12.365-44.745,0l-194.29-194.281C100.226,242.115,97.141,234.018,97.141,225.92z',
            )
            .attr('class', 'link-arrow')
            .attr('transform', 'rotate(180, 225.92, 225.92)');

        // Setup zoom behavior
        this.zoom = zoom<SVGSVGElement, unknown>()
            .scaleExtent([0.1, 4])
            .on('zoom', (event: D3ZoomEvent<SVGSVGElement, unknown>) => {
                this.graphGroup.attr('transform', event.transform.toString());
            });

        this.svg.call(this.zoom as any);

        // Create groups for links and nodes
        this.graphGroup.append('g').attr('class', 'links');
        this.graphGroup.append('g').attr('class', 'nodes');

        this.updateGraph();

        // 1. Setup dynamic collision force with proper radius for each node
        const collide = forceCollide<MemirisSimulationNode>()
            .radius((node: MemirisSimulationNode) => this.getNodeRadius(node) + this.PADDING)
            .iterations(2); // Run collision detection twice per tick for better resolution

        // 2. Setup charge force with stronger repulsion for memory nodes
        const charge = forceManyBody<MemirisSimulationNode>()
            .strength((node: MemirisSimulationNode) => this.getNodeChargeStrength(node))
            .distanceMax(200);

        // 3. Setup link force with variable distances based on link type
        const link = forceLink<MemirisSimulationNode, MemirisSimulationLink>(this.links)
            .id((node: MemirisSimulationNode) => node.getId())
            .distance((link: MemirisSimulationLink) => this.getLinkDistance(link))
            .strength(0.1); // Mid-range spring strength

        // 5. Configure the simulation with all forces
        this.simulation = forceSimulation<MemirisSimulationNode>(this.nodes)
            .force('link', link)
            .force('charge', charge)
            .force('collide', collide)
            .force('x', forceX(this.width / 2).strength(0.0001))
            .force('y', forceY(this.height / 2).strength(0.0001))
            .alpha(2) // Start with high energy
            .alphaDecay(0.03) // Cool down slower to resolve overlaps
            .velocityDecay(0.25) // Lower velocity decay to maintain momentum longer
            .on('tick', () => this.updatePositions());
    }

    /**
     * Updates the graph visualization by binding data to SVG elements,
     * creating or updating nodes, links, and labels based on the current state.
     * This method is called whenever the graph data or settings change.
     */
    updateGraph(): void {
        if (!this.svg) {
            requestAnimationFrame(() => this.initializeGraph());
            return;
        }

        const nodesGroup = this.svg.select<SVGGElement>('.nodes');
        const linksGroup = this.svg.select<SVGGElement>('.links');

        if (!nodesGroup || !linksGroup) {
            return;
        }

        // Remove existing elements to avoid duplication
        nodesGroup.selectAll('*').remove();
        linksGroup.selectAll('*').remove();

        // Create link elements
        this.linkElements = linksGroup
            .selectAll<SVGLineElement, MemirisSimulationLink>('line')
            .data(this.links, (link: MemirisSimulationLink) => link.getId())
            .enter()
            .append('line')
            .attr('class', (link: MemirisSimulationLink) => `link ${this.getLinkClasses(link)}`)
            // Add arrow marker for memory-memory CREATED_FROM connections
            .attr('marker-end', (link: MemirisSimulationLink) => {
                if (link instanceof MemirisSimulationLinkMemoryMemory && link.connection.connection_type === MemirisConnectionType.CREATED_FROM) {
                    return 'url(#arrowhead)';
                }
                return null;
            });

        // Create node elements
        this.nodeElements = nodesGroup
            .selectAll<SVGCircleElement, MemirisSimulationNode>('circle')
            .data(this.nodes, (node: MemirisSimulationNode) => node.getId())
            .enter()
            .append('circle')
            .attr('class', (node: MemirisSimulationNode) => this.getNodeClasses(node))
            .attr('r', (node: MemirisSimulationNode) => this.getNodeRadius(node))
            .call(this.setupDrag() as any)
            .on('click', (_event: MouseEvent, node: MemirisSimulationNode) => this.handleNodeClick(node));

        // Create text labels
        this.textElements = nodesGroup
            .selectAll<SVGTextElement, MemirisSimulationNode>('text')
            .data(this.nodes, (node: MemirisSimulationNode) => node.getId())
            .enter()
            .append('text')
            .text((node: MemirisSimulationNode) => node.getLabel())
            .attr('font-size', '12px')
            .attr('dx', 15)
            .attr('dy', 4)
            .attr('pointer-events', 'none');

        // Create link labels to show connection types
        this.linkLabelElements = linksGroup
            .selectAll<SVGTextElement, MemirisSimulationLink>('text.link-label')
            .data(this.links, (link: MemirisSimulationLink) => link.getId())
            .enter()
            .append('text')
            .attr('class', (link: MemirisSimulationLink) => `link-label ${this.getLinkClasses(link)}`)
            .attr('font-size', '10px')
            .attr('text-anchor', 'middle')
            .attr('dy', -5)
            .attr('pointer-events', 'none')
            .text((link: MemirisSimulationLink) => link.getLabel(this.artemisTranslatePipe));

        // Restart simulation with new nodes and links
        if (this.simulation) {
            this.simulation.nodes(this.nodes);

            const linkForce = this.simulation.force('link') as ForceLink<MemirisSimulationNode, MemirisSimulationLink>;
            if (linkForce) {
                linkForce.links(this.links);
            }

            this.simulation.alpha(0.3).restart();
        }
    }

    /**
     * Initializes and returns a drag behavior for simulation nodes.
     * The drag behavior includes event listeners for `start`, `drag`, and `end`
     * events, allowing interaction with the nodes in the simulation.
     * During the drag events, node positions are updated, and the simulation's alpha target
     * is adjusted so that the simulation continues to run while nodes are being dragged.
     *
     * @return A configured drag behavior for simulation nodes.
     */
    private setupDrag(): DragBehavior<Element, MemirisSimulationNode, MemirisSimulationNode | SubjectPosition> {
        return drag<Element, MemirisSimulationNode>()
            .on('start', (event: D3DragEvent<Element, MemirisSimulationNode, MemirisSimulationNode>, d: MemirisSimulationNode) => {
                if (!event.active && this.simulation) {
                    this.simulation.alphaTarget(0.3).restart();
                }
                d.fx = d.x;
                d.fy = d.y;
            })
            .on('drag', (event: D3DragEvent<Element, MemirisSimulationNode, MemirisSimulationNode>, d: MemirisSimulationNode) => {
                d.fx = event.x;
                d.fy = event.y;
            })
            .on('end', (event: D3DragEvent<Element, MemirisSimulationNode, MemirisSimulationNode>, d: MemirisSimulationNode) => {
                if (!event.active && this.simulation) {
                    this.simulation.alphaTarget(0);
                }
                d.fx = undefined;
                d.fy = undefined;
            });
    }

    /**
     * Updates the positions of link, node, text, and link label elements
     * in the graph visualization based on their source and target coordinates.
     *
     * The method modifies the attributes of the corresponding elements
     * to reflect the current positions of nodes and links in the graph.
     */
    private updatePositions(): void {
        if (!this.linkElements || !this.nodeElements || !this.textElements) return;

        this.linkElements
            .attr('x1', (link: MemirisSimulationLink) => link.source.x || 0)
            .attr('y1', (link: MemirisSimulationLink) => link.source.y || 0)
            .attr('x2', (link: MemirisSimulationLink) => link.target.x || 0)
            .attr('y2', (link: MemirisSimulationLink) => link.target.y || 0);

        this.nodeElements.attr('cx', (node: MemirisSimulationNode) => node.x || 0).attr('cy', (node: MemirisSimulationNode) => node.y || 0);

        this.textElements.attr('x', (node: MemirisSimulationNode) => node.x || 0).attr('y', (node: MemirisSimulationNode) => node.y || 0);

        if (this.linkLabelElements) {
            this.linkLabelElements
                // Position link labels at the midpoint of the link
                .attr('x', (link: MemirisSimulationLink) => {
                    const sourceX = link.source.x || 0;
                    const targetX = link.target.x || 0;
                    return (sourceX + targetX) / 2;
                })
                .attr('y', (link: MemirisSimulationLink) => {
                    const sourceY = link.source.y || 0;
                    const targetY = link.target.y || 0;
                    return (sourceY + targetY) / 2;
                })
                // Rotate link labels to align with the link
                .attr('transform', (link: MemirisSimulationLink) => {
                    const sourceX = link.source.x || 0;
                    const sourceY = link.source.y || 0;
                    const targetX = link.target.x || 0;
                    const targetY = link.target.y || 0;

                    // Calculate angle of the link
                    const dx = targetX - sourceX;
                    const dy = targetY - sourceY;
                    const angle = (Math.atan2(dy, dx) * 180) / Math.PI;

                    // Prevent upside-down text by flipping it
                    let adjustedAngle = angle;
                    if (angle > 90 || angle < -90) {
                        adjustedAngle = angle - 180;
                    }

                    // Rotate text to align with the link
                    return `rotate(${adjustedAngle}, ${(sourceX + targetX) / 2}, ${(sourceY + targetY) / 2})`;
                });
        }
    }

    /**
     * Applies the specified settings to nodes and links in the graph.
     * Nodes and links that do not meet the filter criteria are excluded.
     *
     * @return {void} Doesn't return a value.
     */
    private applySettings(): void {
        // Apply settings to nodes
        this.nodes = this.allNodes.filter((node) => {
            // Filter out memory nodes when showMemories is false
            if (node instanceof MemirisMemoryNode && !this.settings().showMemories) {
                return false;
            }

            // Filter out learning nodes when showLearnings is false
            if (node instanceof MemirisLearningNode && !this.settings().showLearnings) {
                return false;
            }

            // Filter out deleted memory nodes when hideDeleted is true
            return !(node instanceof MemirisMemoryNode && this.settings().hideDeleted && node.memory.deleted);
        });

        // Apply settings to links
        this.links = this.allLinks.filter((link) => {
            if (!this.settings().showConnections) return false;

            // Only include links where both source and target nodes are visible after filtering
            const sourceId = link.source.getId();
            const targetId = link.target.getId();

            const sourceNode = this.nodes.find((node) => node.getId() === sourceId);
            const targetNode = this.nodes.find((node) => node.getId() === targetId);

            return Boolean(sourceNode && targetNode);
        });
    }

    /**
     * Handles the click event on a node in the simulation.
     * Emits the selected node and highlights it.
     *
     * @param {MemirisSimulationNode} node - The node object that was clicked.
     */
    private handleNodeClick(node: MemirisSimulationNode): void {
        this.selectedNode = node;
        this.nodeSelected.emit(node);
        this.highlightNode(node);
    }

    /**
     * Highlights a specific node in the simulation by applying the "selected" class.
     * Removes the highlight from all other nodes.
     *
     * @param {MemirisSimulationNode} node - The node to highlight; pass undefined to clear all highlights.
     */
    private highlightNode(node?: MemirisSimulationNode): void {
        if (!this.nodeElements) return;
        // Remove highlight from all nodes
        this.nodeElements.classed('selected', false);

        if (node) {
            this.nodeElements.filter((candidate_node: MemirisSimulationNode) => candidate_node.getId() === node.getId()).classed('selected', true);
        }
    }

    /**
     * Zooms in the graph view by scaling it up by a factor of 1.2.
     * Uses D3's zoom behavior to apply the transformation.
     */
    zoomIn(): void {
        this.svg?.transition().call(this.zoom?.scaleBy as any, 1.2);
    }

    /**
     * Zooms out the graph view by scaling it down by a factor of 0.8.
     * Uses D3's zoom behavior to apply the transformation.
     */
    zoomOut(): void {
        this.svg?.transition().call(this.zoom?.scaleBy as any, 0.8);
    }

    /**
     * Resets the view of the graph to fit the entire content within the SVG container.
     * Calculates the bounding box of the graph content and applies a zoom transformation
     * to center and scale it appropriately.
     */
    resetView(): void {
        if (!this.svg || !this.zoom || !this.graphGroup || !this.svgContainer()) return;

        const svgElement = this.svgContainer()!.nativeElement;
        const width = svgElement.clientWidth;
        const height = svgElement.clientHeight;

        // Get bounding box of the graph content
        const groupElement = this.graphGroup.node();
        if (!groupElement) return;
        const bbox = groupElement.getBBox();
        if (bbox.width === 0 || bbox.height === 0) return;

        // Calculate scale and translation to fit and center the graph
        const scale = Math.min(width / bbox.width, height / bbox.height) * 0.8;
        const x = width / 2 - (bbox.x + bbox.width / 2) * scale;
        const y = height / 2 - (bbox.y + bbox.height / 2) * scale;

        this.svg.transition().call(this.zoom.transform as any, zoomIdentity.translate(x, y).scale(scale));
    }

    /**
     * Returns the CSS classes for a given node based on its type and state.
     *
     * @param {MemirisSimulationNode} node - The node for which to get the classes.
     * @return {string} The CSS class string for the node.
     */
    private getNodeClasses(node: MemirisSimulationNode): string {
        let classes = 'node';

        if (node instanceof MemirisMemoryNode) {
            classes += ' memory';
            if (node.memory.deleted) {
                classes += ' deleted';
            }
        } else if (node instanceof MemirisLearningNode) {
            classes += ' learning';
        }

        if (this.selectedNode?.getId() === node.getId()) {
            classes += ' selected';
        }

        return classes;
    }

    /**
     * Returns the CSS classes for a given link based on its type.
     *
     * @param {MemirisSimulationLink} link - The link for which to get the classes.
     * @return {string} The CSS class string for the link.
     */
    private getLinkClasses(link: MemirisSimulationLink): string {
        let classes = '';

        if (link instanceof MemirisSimulationLinkMemoryMemory) {
            classes += ' memory-memory';
        } else if (link instanceof MemirisSimulationLinkMemoryLearning) {
            classes += ' memory-learning';
        }

        return classes;
    }

    /**
     * Returns the radius for a given node based on its type.
     *
     * @param {MemirisSimulationNode} node - The node for which to get the radius.
     * @return {number} The radius in pixels for the node.
     */
    private getNodeRadius(node: MemirisSimulationNode): number {
        if (node instanceof MemirisMemoryNode) {
            return 12;
        } else if (node instanceof MemirisLearningNode) {
            return 8;
        } else {
            return 4;
        }
    }

    /**
     * Returns the charge strength for a given node based on its type.
     * Memory nodes have a stronger negative charge than learning nodes,
     * which helps to keep them spaced out in the simulation.
     *
     * @param {MemirisSimulationNode} node - The node for which to get the charge strength.
     * @return {number} The charge strength in pixels.
     */
    private getNodeChargeStrength(node: MemirisSimulationNode): number {
        if (node instanceof MemirisMemoryNode) {
            return -350;
        } else if (node instanceof MemirisLearningNode) {
            return -250;
        } else {
            return -1000;
        }
    }

    /**
     * Returns the distance for a link based on its type.
     * Memory-memory links have a longer distance than memory-learning links,
     * which helps to keep everything spaced out in the simulation.
     *
     * @param {MemirisSimulationLink} link - The link for which to get the distance.
     * @return {number} The distance in pixels.
     */
    private getLinkDistance(link: MemirisSimulationLink): number {
        if (link instanceof MemirisSimulationLinkMemoryMemory) {
            return 400;
        } else if (link instanceof MemirisSimulationLinkMemoryLearning) {
            return 120;
        } else {
            return 100;
        }
    }

    /**
     * Initializes the nodes in a circular layout to prevent initial overlapping.
     * This method positions the nodes in a circle based on their index,
     * spreading them out evenly around the circumference.
     */
    private initializeCircularLayout(): void {
        const radius = Math.min(this.width, this.height); // Radius of the circle
        const centerX = this.width / 2;
        const centerY = this.height / 2;

        this.nodes.forEach((node, index) => {
            // Calculate the angle for this node
            const angle = (index / this.nodes.length) * 2 * Math.PI;

            // Calculate x and y positions based on the angle
            node.x = centerX + radius * Math.cos(angle);
            node.y = centerY + radius * Math.sin(angle);

            // Reset fixed positions
            node.fx = undefined;
            node.fy = undefined;
        });
    }
}
