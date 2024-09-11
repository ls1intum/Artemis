package de.tum.cit.aet.artemis.atlas.service.competency;

import static de.tum.cit.aet.artemis.atlas.domain.competency.StandardizedCompetency.FIRST_VERSION;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import jakarta.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.atlas.domain.competency.KnowledgeArea;
import de.tum.cit.aet.artemis.atlas.domain.competency.Source;
import de.tum.cit.aet.artemis.atlas.domain.competency.StandardizedCompetency;
import de.tum.cit.aet.artemis.atlas.repository.SourceRepository;
import de.tum.cit.aet.artemis.atlas.repository.competency.KnowledgeAreaRepository;
import de.tum.cit.aet.artemis.atlas.repository.competency.StandardizedCompetencyRepository;
import de.tum.cit.aet.artemis.web.rest.dto.standardizedCompetency.SourceDTO;
import de.tum.cit.aet.artemis.web.rest.dto.standardizedCompetency.StandardizedCompetencyCatalogDTO;
import de.tum.cit.aet.artemis.web.rest.dto.standardizedCompetency.StandardizedCompetencyCatalogDTO.KnowledgeAreaForCatalogDTO;
import de.tum.cit.aet.artemis.web.rest.dto.standardizedCompetency.StandardizedCompetencyCatalogDTO.StandardizedCompetencyForCatalogDTO;
import de.tum.cit.aet.artemis.web.rest.dto.standardizedCompetency.StandardizedCompetencyRequestDTO;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorException;

/**
 * Service for managing {@link StandardizedCompetency} entities.
 */
@Profile(PROFILE_CORE)
@Service
public class StandardizedCompetencyService {

    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Logger log = LoggerFactory.getLogger(StandardizedCompetencyService.class);

    private final StandardizedCompetencyRepository standardizedCompetencyRepository;

    private final KnowledgeAreaRepository knowledgeAreaRepository;

    private final SourceRepository sourceRepository;

    public StandardizedCompetencyService(StandardizedCompetencyRepository standardizedCompetencyRepository, KnowledgeAreaRepository knowledgeAreaRepository,
            SourceRepository sourceRepository) {
        this.standardizedCompetencyRepository = standardizedCompetencyRepository;
        this.knowledgeAreaRepository = knowledgeAreaRepository;
        this.sourceRepository = sourceRepository;
    }

    /**
     * Verifies a standardized competency and then saves it to the database
     *
     * @param competency the standardized competency to create
     * @return the created standardized competency
     */
    public StandardizedCompetency createStandardizedCompetency(StandardizedCompetencyRequestDTO competency) {

        KnowledgeArea knowledgeArea = knowledgeAreaRepository.findByIdElseThrow(competency.knowledgeAreaId());
        Source source = null;
        if (competency.sourceId() != null) {
            source = sourceRepository.findByIdElseThrow(competency.sourceId());
        }

        var competencyToCreate = new StandardizedCompetency(competency.title().trim(), competency.description(), competency.taxonomy(), FIRST_VERSION);
        competencyToCreate.setKnowledgeArea(knowledgeArea);
        competencyToCreate.setSource(source);

        competencyToCreate = standardizedCompetencyRepository.save(competencyToCreate);
        // set the reference to the first version of this standardized competency (itself)
        competencyToCreate.setFirstVersion(competencyToCreate);
        return standardizedCompetencyRepository.save(competencyToCreate);
    }

    /**
     * Updates an existing standardized competency with the provided competency data
     *
     * @param competencyId the id of the competency to update
     * @param competency   competency object containing the data to update
     * @return the updated standardized competency
     */
    public StandardizedCompetency updateStandardizedCompetency(long competencyId, StandardizedCompetencyRequestDTO competency) {
        var existingCompetency = standardizedCompetencyRepository.findByIdElseThrow(competencyId);

        existingCompetency.setTitle(competency.title());
        existingCompetency.setDescription(competency.description());
        existingCompetency.setTaxonomy(competency.taxonomy());

        if (competency.sourceId() == null) {
            existingCompetency.setSource(null);
        }
        else if (existingCompetency.getSource() == null || !competency.sourceId().equals(existingCompetency.getSource().getId())) {
            var source = sourceRepository.findByIdElseThrow(competency.sourceId());
            existingCompetency.setSource(source);
        }

        if (!competency.knowledgeAreaId().equals(existingCompetency.getKnowledgeArea().getId())) {
            var knowledgeArea = knowledgeAreaRepository.findByIdElseThrow(competency.knowledgeAreaId());
            existingCompetency.setKnowledgeArea(knowledgeArea);
        }

        return standardizedCompetencyRepository.save(existingCompetency);
    }

    /**
     * Deletes an existing standardized competency with the given id or throws an EntityNotFoundException
     *
     * @param competencyId the id of the competency to delete
     */
    public void deleteStandardizedCompetencyElseThrow(long competencyId) throws EntityNotFoundException {
        if (!standardizedCompetencyRepository.existsById(competencyId)) {
            throw new EntityNotFoundException("StandardizedCompetency", competencyId);
        }
        standardizedCompetencyRepository.deleteById(competencyId);
    }

    /**
     * Gets a hierarchical structure of all knowledge areas including their competencies, sorted by their title
     *
     * @return the list of knowledge areas with no parent, containing all their descendants and competencies
     */
    public List<KnowledgeArea> getAllForTreeView() {
        var knowledgeAreasForTreeView = new ArrayList<KnowledgeArea>();
        var idMap = new HashMap<Long, KnowledgeArea>();

        var knowledgeAreas = knowledgeAreaRepository.findAllWithCompetenciesByOrderByTitleAsc();
        for (var knowledgeArea : knowledgeAreas) {
            // use a linked hash set to retain order
            knowledgeArea.setChildren(new LinkedHashSet<>());
            idMap.put(knowledgeArea.getId(), knowledgeArea);
        }

        for (var knowledgeArea : knowledgeAreas) {
            // if the knowledge area has no parent add it to the result list
            if (knowledgeArea.getParent() == null) {
                knowledgeAreasForTreeView.add(knowledgeArea);
                continue;
            }
            // otherwise add it to the children of its parents
            var parent = idMap.get(knowledgeArea.getParent().getId());
            if (parent == null) {
                continue;
            }
            parent.addToChildren(knowledgeArea);
        }

        return knowledgeAreasForTreeView;
    }

    /**
     * Imports the catalog of knowledge areas, standardized competencies and sources given in the DTO into Artemis
     *
     * @param standardizedCompetencyCatalogDTO the DTO containing the data to import
     */
    public void importStandardizedCompetencyCatalog(StandardizedCompetencyCatalogDTO standardizedCompetencyCatalogDTO) {
        List<KnowledgeAreaForCatalogDTO> topLevelKnowledgeAreas = standardizedCompetencyCatalogDTO.knowledgeAreas() != null ? standardizedCompetencyCatalogDTO.knowledgeAreas()
                : Collections.emptyList();
        List<SourceDTO> sourceDTOs = standardizedCompetencyCatalogDTO.sources() != null ? standardizedCompetencyCatalogDTO.sources() : Collections.emptyList();
        var sourceIds = sourceDTOs.stream().map(SourceDTO::id).toList();

        for (var knowledgeAreaDTO : topLevelKnowledgeAreas) {
            verifySourcesForSelfAndDescendants(knowledgeAreaDTO, sourceIds);
        }

        var sourceMap = new HashMap<Long, Source>();
        for (var sourceDTO : sourceDTOs) {
            Long oldId = sourceDTO.id();
            var source = sourceRepository.save(new Source(sourceDTO.title(), sourceDTO.author(), sourceDTO.uri()));
            sourceMap.put(oldId, source);
        }

        for (var knowledgeAreaDTO : topLevelKnowledgeAreas) {
            importSelfAndDescendants(knowledgeAreaDTO, null, sourceMap);
        }
    }

    /**
     * Returns a pretty-printed JSON string containing the catalog of knowledge areas, standardized competencies and sources of this Artemis instance.
     *
     * @return the JSON string containing the catalog to export
     */
    public String exportStandardizedCompetencyCatalog() {
        List<KnowledgeArea> knowledgeAreas = getAllForTreeView();
        List<Source> sources = sourceRepository.findAll();
        var catalog = StandardizedCompetencyCatalogDTO.of(knowledgeAreas, sources);

        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(catalog);
        }
        catch (JsonProcessingException e) {
            var error = "Error pretty printing JSON";
            log.error(error, e);
            throw new InternalServerErrorException(error);
        }
    }

    /**
     * Recursively verifies that all competencies in the given knowledge area and its descendants have a sourceId that is contained in the given sourceId list (or don't have a
     * source)
     *
     * @param knowledgeArea the knowledge area to check
     * @param sourceIds     the list of valid source ids
     */
    private void verifySourcesForSelfAndDescendants(KnowledgeAreaForCatalogDTO knowledgeArea, List<Long> sourceIds) {
        List<KnowledgeAreaForCatalogDTO> children = knowledgeArea.children() != null ? knowledgeArea.children() : Collections.emptyList();
        List<StandardizedCompetencyForCatalogDTO> competencies = knowledgeArea.competencies() != null ? knowledgeArea.competencies() : Collections.emptyList();

        for (var competency : competencies) {
            var sourceId = competency.sourceId();
            if (sourceId != null && !sourceIds.contains(sourceId)) {
                throw new BadRequestException("The source with id " + sourceId + " used in the competency \"" + competency.title() + "\" does not exist in your import file!");
            }
        }
        for (var child : children) {
            verifySourcesForSelfAndDescendants(child, sourceIds);
        }
    }

    /**
     * Recursively imports a knowledge area, its competencies and its children into Artemis
     *
     * @param knowledgeArea the knowledge area to import
     * @param parent        the parent of the knowledge area or null (needed to set the parent attribute)
     * @param sourceMap     the map of sourceId to source object
     */
    private void importSelfAndDescendants(KnowledgeAreaForCatalogDTO knowledgeArea, KnowledgeArea parent, Map<Long, Source> sourceMap) {
        // import self without competencies and children
        var knowledgeAreaToImport = new KnowledgeArea(knowledgeArea.title(), knowledgeArea.shortTitle(), knowledgeArea.description());
        knowledgeAreaToImport.setParent(parent);
        var importedKnowledgeArea = knowledgeAreaRepository.save(knowledgeAreaToImport);

        // import all competencies
        if (knowledgeArea.competencies() != null) {
            var competenciesToImport = new ArrayList<StandardizedCompetency>();
            for (var competency : knowledgeArea.competencies()) {
                var version = competency.version() != null ? competency.version() : FIRST_VERSION;
                var source = competency.sourceId() != null ? sourceMap.get(competency.sourceId()) : null;
                var competencyToImport = new StandardizedCompetency(competency.title(), competency.description(), competency.taxonomy(), version);
                competencyToImport.setKnowledgeArea(importedKnowledgeArea);
                competencyToImport.setSource(source);
                competenciesToImport.add(competencyToImport);
            }
            var importedCompetencies = standardizedCompetencyRepository.saveAll(competenciesToImport);
            importedCompetencies.forEach(competency -> competency.setFirstVersion(competency));
            standardizedCompetencyRepository.saveAll(importedCompetencies);
        }

        // import all children
        if (knowledgeArea.children() != null) {
            for (var child : knowledgeArea.children()) {
                importSelfAndDescendants(child, importedKnowledgeArea, sourceMap);
            }
        }
    }
}
