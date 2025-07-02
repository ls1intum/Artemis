import { Component, ElementRef, OnChanges, OnDestroy, SimpleChanges, computed, input, output, viewChild } from '@angular/core';
import { LoadingIndicatorContainerComponent } from 'app/shared/loading-indicator-container/loading-indicator-container.component';
import { faArrowsToEye, faHexagonNodes, faMinus, faPlus } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { Subscription } from 'rxjs';
import {
    MemirisConnectionType,
    MemirisGraphData,
    MemirisGraphFilters,
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

@Component({
    selector: 'jhi-memiris-graph-view',
    imports: [LoadingIndicatorContainerComponent, FontAwesomeModule],
    templateUrl: './memiris-graph-view.component.html',
    styleUrl: './memiris-graph-view.component.scss',
})
export class MemirisGraphViewComponent implements OnDestroy, OnChanges {
    svgContainer = viewChild<ElementRef>('svgContainer');

    graphData = input<MemirisGraphData>();
    initialSelectedMemoryId = input<string>();
    filters = input<MemirisGraphFilters>(new MemirisGraphFilters());

    nodeSelected = output<MemirisSimulationNode>();

    // Icons
    faPlus = faPlus;
    faMinus = faMinus;
    faArrowsToEye = faArrowsToEye;
    faHexagonNodes = faHexagonNodes;

    // Internal state
    loading = computed(() => !this.graphData());
    selectedNode?: MemirisSimulationNode;
    nodes: MemirisSimulationNode[] = [];
    links: MemirisSimulationLink[] = [];
    allNodes: MemirisSimulationNode[] = [];
    allLinks: MemirisSimulationLink[] = [];

    // D3-related properties
    private simulation: Simulation<MemirisSimulationNode, MemirisSimulationLink> | null = null;
    private svg: Selection<SVGSVGElement, unknown, null, undefined> | null = null;
    private linkElements: Selection<SVGLineElement, MemirisSimulationLink, SVGGElement, unknown> | null = null;
    private nodeElements: Selection<SVGCircleElement, MemirisSimulationNode, SVGGElement, unknown> | null = null;
    private textElements: Selection<SVGTextElement, MemirisSimulationNode, SVGGElement, unknown> | null = null;
    private linkLabelElements: Selection<SVGTextElement, MemirisSimulationLink, SVGGElement, unknown> | null = null;
    private zoom: ZoomBehavior<SVGSVGElement, unknown> | null = null;
    private subscriptions: Subscription[] = [];
    private width = 0;
    private height = 0;
    private graphGroup: Selection<SVGGElement, unknown, null, undefined>;

    ngOnChanges(changes: SimpleChanges): void {
        if (changes['filters']) {
            this.applyFilters();
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

    ngOnDestroy(): void {
        this.subscriptions.forEach((sub) => sub.unsubscribe());
        if (this.simulation) {
            this.simulation.stop();
        }
    }

    /**
     * Updates the graph data by creating nodes and links based on the provided `MemirisGraphData`.
     * This method processes memories, learnings, and connections to populate the graph structure
     * and applies filters before then updating the graph visualization.
     *
     * @param {MemirisGraphData} data - The input data for the graph, containing memories, learnings, and connections.
     */
    private updateGraphData(data: MemirisGraphData) {
        if (!data) {
            return;
        }
        // eslint-disable-next-line no-undef
        console.log('Updating graph data:', data);
        // Create nodes from graph data
        this.allNodes = data.memories.map((memory) => new MemirisMemoryNode(memory));
        this.allNodes.push(...data.learnings.map((learning) => new MemirisLearningNode(learning)));

        // Create links from graph data
        this.allLinks = [];

        // Create memory-learning links
        data.memories.forEach((memory) => {
            const memoryNode = this.allNodes.find((node) => node.getId() === memory.id);
            if (!memoryNode) return;

            memory.learnings?.forEach((learningId) => {
                const learning = data.learnings.find((l) => l.id === learningId);
                const learningNode = this.allNodes.find((node) => node.getId() === learningId);
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
        });

        this.applyFilters();

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

        // Create SVG element
        this.svg = select(element).attr('width', '100%').attr('height', '100%').attr('viewBox', [0, 0, this.width, this.height]);

        // Clear any existing content
        this.svg.selectAll('*').remove();

        // Create container group for zoom
        this.graphGroup = this.svg.append('g');

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

        // Define padding for collision detection
        const PADDING = 8; // extra "personal-space" in pixels

        // 1. Setup dynamic collision force with proper radius for each node
        const collide = forceCollide<MemirisSimulationNode>()
            .radius((node) => this.getNodeRadius(node) + PADDING) // Use node's actual radius + padding
            .iterations(2); // Run collision detection twice per tick for better resolution

        // 2. Setup charge force with stronger repulsion for memory nodes
        const charge = forceManyBody<MemirisSimulationNode>()
            .strength((node) => this.getNodeChargeStrength(node))
            .distanceMax(200);

        // 3. Setup link force with variable distances based on link type
        const link = forceLink<MemirisSimulationNode, MemirisSimulationLink>(this.links)
            .id((node) => node.getId())
            .distance((link) => this.getLinkDistance(link))
            .strength(0.1); // Mid-range spring strength

        // 4. Setup center force with mild strength
        // const center = forceCenter(this.width / 2, this.height / 2).strength(0.05);

        // 5. Configure the simulation with all forces
        this.simulation = forceSimulation<MemirisSimulationNode>(this.nodes)
            .force('link', link)
            .force('charge', charge)
            .force('collide', collide)
            .force('x', forceX(this.width / 2).strength(0.0001))
            .force('y', forceY(this.height / 2).strength(0.0001))
            .alpha(1) // Start with high energy
            .alphaDecay(0.03) // Cool down slower to resolve overlaps
            .velocityDecay(0.25) // Lower velocity decay to maintain momentum longer
            .on('tick', () => this.updatePositions());
    }

    /**
     * Updates the graph visualization by binding data to SVG elements,
     * creating or updating nodes, links, and labels based on the current state.
     * This method is called whenever the graph data or filters change.
     */
    updateGraph(): void {
        if (!this.svg) {
            requestAnimationFrame(() => this.initializeGraph());
            return;
        }

        const nodesGroup = this.svg.select<SVGGElement>('.nodes');
        const linksGroup = this.svg.select<SVGGElement>('.links');

        if (!nodesGroup || !linksGroup) return;

        // Create link elements
        this.linkElements = linksGroup
            .selectAll<SVGLineElement, MemirisSimulationLink>('line')
            .data(this.links, (link) => link.getId())
            .enter()
            .append('line')
            .attr('class', (node) => this.getLinkClasses(node))
            .attr('stroke', (node) => this.getLinkColor(node))
            .attr('stroke-width', 1.5);

        // Create node elements
        this.nodeElements = nodesGroup
            .selectAll<SVGCircleElement, MemirisSimulationNode>('circle')
            .data(this.nodes, (node) => node.getId())
            .enter()
            .append('circle')
            .attr('class', (node) => this.getNodeClasses(node))
            .attr('r', (node) => this.getNodeRadius(node))
            .attr('fill', (node) => this.getNodeColor(node))
            .call(this.setupDrag() as any)
            .on('click', (_event: MouseEvent, node: MemirisSimulationNode) => this.handleNodeClick(node));

        // Create text labels
        this.textElements = nodesGroup
            .selectAll<SVGTextElement, MemirisSimulationNode>('text')
            .data(this.nodes, (node) => node.getId())
            .enter()
            .append('text')
            .text((node) => node.getLabel())
            .attr('font-size', '12px')
            .attr('dx', 15)
            .attr('dy', 4)
            .attr('pointer-events', 'none');

        // Create link labels to show connection types
        this.linkLabelElements = linksGroup
            .selectAll<SVGTextElement, MemirisSimulationLink>('text.link-label')
            .data(this.links, (link) => link.getId())
            .enter()
            .append('text')
            .attr('class', 'link-label')
            .attr('font-size', '10px')
            .attr('text-anchor', 'middle')
            .attr('dy', -5)
            .attr('pointer-events', 'none')
            .style('fill', (link) => this.getLinkColor(link), 'important')
            .text((node) => node.getLabel());

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
                d.fx = null;
                d.fy = null;
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
            .attr('x1', (link) => link.source.x || 0)
            .attr('y1', (link) => link.source.y || 0)
            .attr('x2', (link) => link.target.x || 0)
            .attr('y2', (link) => link.target.y || 0);

        this.nodeElements.attr('cx', (node) => node.x || 0).attr('cy', (node) => node.y || 0);

        this.textElements.attr('x', (node) => node.x || 0).attr('y', (node) => node.y || 0);

        if (this.linkLabelElements) {
            this.linkLabelElements
                // Position link labels at the midpoint of the link
                .attr('x', (d) => {
                    const sourceX = d.source.x || 0;
                    const targetX = d.target.x || 0;
                    return (sourceX + targetX) / 2;
                })
                .attr('y', (d) => {
                    const sourceY = d.source.y || 0;
                    const targetY = d.target.y || 0;
                    return (sourceY + targetY) / 2;
                })
                // Rotate link labels to align with the link
                .attr('transform', (d) => {
                    const sourceX = d.source.x || 0;
                    const sourceY = d.source.y || 0;
                    const targetX = d.target.x || 0;
                    const targetY = d.target.y || 0;

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
     * Applies the specified filters to nodes and links in the graph.
     * Nodes and links that do not meet the filter criteria are excluded.
     *
     * @return {void} Doesn't return a value.
     */
    private applyFilters(): void {
        // Apply filters to nodes
        this.nodes = this.allNodes.filter((node) => {
            // Filter out memory nodes when showMemories is false
            if (node instanceof MemirisMemoryNode && !this.filters().showMemories) {
                return false;
            }

            // Filter out learning nodes when showLearnings is false
            if (node instanceof MemirisLearningNode && !this.filters().showLearnings) {
                return false;
            }

            // Filter out deleted memory nodes when hideDeleted is true
            return !(node instanceof MemirisMemoryNode && this.filters().hideDeleted && node.memory.deleted);
        });

        // Apply filters to links
        this.links = this.allLinks.filter((link) => {
            if (!this.filters().showConnections) return false;

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
        if (!this.nodeElements || !this.linkElements) return;
        // Remove highlight from all nodes
        this.nodeElements.classed('selected', false);

        if (node) {
            this.nodeElements.filter((candidate_node) => candidate_node.getId() === node.getId()).classed('selected', true);
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
     * Returns the color for a given node based on its type.
     *
     * @param {MemirisSimulationNode} node - The node for which to get the color.
     * @return {string} The color code for the node.
     */
    private getNodeColor(node: MemirisSimulationNode): string {
        // Check if MemirisSimulationNode is an instance of MemirisMemoryNode or MemirisLearningNode to decide color
        if (node instanceof MemirisMemoryNode) {
            return '#4f86f7';
        } else if (node instanceof MemirisLearningNode) {
            return '#f7a14f';
        } else {
            // eslint-disable-next-line no-undef
            console.warn('Unknown node type:', node);
            return '#ccc';
        }
    }

    /**
     * Returns the color for a given link based on its type.
     *
     * @param {MemirisSimulationLink} link - The link for which to get the color.
     * @return {string} The color code for the link.
     */
    private getLinkColor(link: MemirisSimulationLink): string {
        if (link instanceof MemirisSimulationLinkMemoryMemory) {
            return '#6c8ebf';
        } else if (link instanceof MemirisSimulationLinkMemoryLearning) {
            return '#d79b00';
        } else {
            // eslint-disable-next-line no-undef
            console.warn('Unknown link type:', link);
            return '#ddd';
        }
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
        let classes = 'link';

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
            // eslint-disable-next-line no-undef
            console.warn('Unknown node type:', node);
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
            // eslint-disable-next-line no-undef
            console.warn('Unknown node type:', node);
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
            // eslint-disable-next-line no-undef
            console.warn('Unknown link type:', link);
            return 100;
        }
    }
}
