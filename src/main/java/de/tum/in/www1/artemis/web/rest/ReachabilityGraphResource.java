package de.tum.in.www1.artemis.web.rest;

import static com.google.gson.JsonParser.parseString;
import static de.tum.in.www1.artemis.service.compass.umlmodel.reachabilitygraph.ReachabilityGraphArc.REACHABILITY_GRAPH_ARC_TYPE;
import static de.tum.in.www1.artemis.service.compass.umlmodel.reachabilitygraph.ReachabilityGraphMarking.REACHABILITY_GRAPH_MARKING_TYPE;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.badRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.service.CoverabilityGraphService;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.parsers.UMLModelParser;
import de.tum.in.www1.artemis.service.compass.umlmodel.petrinet.PetriNet;
import de.tum.in.www1.artemis.service.dto.uml.UMLElementDTO;
import de.tum.in.www1.artemis.service.dto.uml.UMLModelDTO;
import de.tum.in.www1.artemis.service.dto.uml.UMLReachabilityGraphMarkingDTO;
import de.tum.in.www1.artemis.service.dto.uml.UMLRelationshipDTO;

/** REST controller for managing TestResource. */
@RestController
@RequestMapping(ReachabilityGraphResource.Endpoints.ROOT)
public class ReachabilityGraphResource {

    private final Logger log = LoggerFactory.getLogger(ReachabilityGraphResource.class);

    private static final String[] ANCHOR_POINTS = new String[] { "Right", "Down", "Left", "Up" };

    private final CoverabilityGraphService coverabilityGraphService;

    public ReachabilityGraphResource(CoverabilityGraphService coverabilityGraphService) {
        this.coverabilityGraphService = coverabilityGraphService;
    }

    @PostMapping("/reachability-graph")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<UMLModelDTO> getTest(@RequestBody String model) throws IOException {
        UMLDiagram diagram = UMLModelParser.buildModelFromJSON(parseString(model).getAsJsonObject(), 0);
        CoverabilityGraphService.CoverabilityGraph coverabilityGraph = coverabilityGraphService.coverabilityGraph((PetriNet) diagram);

        if (coverabilityGraph == null) {
            return badRequest();
        }

        Map<List<Integer>, String> uuids = coverabilityGraph.getVertices().stream()
                .collect(Collectors.toMap(marking -> marking, marking -> UUID.randomUUID().toString(), (a, b) -> b));

        int offset = 0;
        List<UMLElementDTO> elements = new ArrayList<>();

        for (List<Integer> marking : coverabilityGraph.getVertices()) {
            elements.add(new UMLReachabilityGraphMarkingDTO(uuids.get(marking), marking.toString(), REACHABILITY_GRAPH_MARKING_TYPE,
                    new UMLElementDTO.Bounds(offset, offset, 200, 100), coverabilityGraph.getInitialMarking().equals(marking)));
            offset -= 20;
        }

        Map<List<Integer>, Integer> anchors = coverabilityGraph.getVertices().stream().collect(Collectors.toMap(marking -> marking, marking -> 0, (a, b) -> b));

        List<UMLRelationshipDTO> relationships = new ArrayList<>();

        coverabilityGraph.getEdges().forEach((source, targets) -> targets.forEach(target -> {
            List<Integer> vertex = target.getVertex();
            Integer anchor = anchors.get(source);

            relationships.add(new UMLRelationshipDTO(UUID.randomUUID().toString(), target.getEdge().getName(), REACHABILITY_GRAPH_ARC_TYPE, new UMLElementDTO.Bounds(0, 0, 0, 0),
                    new ArrayList<>() {

                        {
                            add(new UMLRelationshipDTO.Coordinates(0, 0));
                        }
                    }, new UMLRelationshipDTO.RelationshipAnchor(ANCHOR_POINTS[anchor], uuids.get(source)),
                    new UMLRelationshipDTO.RelationshipAnchor(ANCHOR_POINTS[(anchor + 2) % 4], uuids.get(vertex))));

            anchors.put(source, (anchor + 1) % 4);
        }));

        return ResponseEntity.ok(new UMLModelDTO("2.0.0", "ReachabilityGraph", new UMLModelDTO.Size(100, 100), elements, relationships));
    }

    public static final class Endpoints {

        public static final String ROOT = "/api";
    }
}
