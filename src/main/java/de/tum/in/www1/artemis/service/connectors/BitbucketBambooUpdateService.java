package de.tum.in.www1.artemis.service.connectors;

import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.exception.BambooException;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.ApplicationLinksDTO;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.BambooRepositoryDTO;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.BambooTriggerDTO;
import de.tum.in.www1.artemis.service.connectors.bitbucket.dto.BitbucketRepositoryDTO;

@Service
// Only activate this service bean, if both Bamboo and Bitbucket are activated (@Profile({"bitbucket","bamboo"}) would activate
// this if any profile is active (OR). We want both (AND)
@Profile("bamboo & bitbucket")
public class BitbucketBambooUpdateService implements ContinuousIntegrationUpdateService {

    @Value("${artemis.continuous-integration.url}")
    private URL bambooServerUrl;

    @Value("${artemis.version-control.url}")
    private URL bitbucketServerUrl;

    @Value("${artemis.continuous-integration.vcs-application-link-name}")
    private String vcsApplicationLinkName;

    private static final String OLD_ASSIGNMENT_REPO_NAME = "Assignment";

    private final Logger log = LoggerFactory.getLogger(BitbucketBambooUpdateService.class);

    private final RestTemplate bambooRestTemplate;

    private final RestTemplate bitbucketRestTemplate;

    // application link name --> Link
    private final Map<String, ApplicationLinksDTO.ApplicationLinkDTO> cachedApplicationLinks = new ConcurrentHashMap<>();

    public BitbucketBambooUpdateService(@Qualifier("bambooRestTemplate") RestTemplate bambooRestTemplate, @Qualifier("bitbucketRestTemplate") RestTemplate bitbucketRestTemplate) {
        this.bambooRestTemplate = bambooRestTemplate;
        this.bitbucketRestTemplate = bitbucketRestTemplate;
    }

    @Override
    public void updatePlanRepository(String bambooProjectKey, String buildPlanKey, String bambooRepositoryName, String bitbucketProjectKey, String bitbucketRepositoryName,
            String branchName, Optional<List<String>> optionalTriggeredByRepositories) {
        try {
            log.debug("Update plan repository for build plan {}", buildPlanKey);
            BambooRepositoryDTO bambooRepository = findBambooRepository(bambooRepositoryName, OLD_ASSIGNMENT_REPO_NAME, buildPlanKey);
            if (bambooRepository == null) {
                throw new BambooException("Something went wrong while updating the template repository of the build plan " + buildPlanKey
                        + " to the student repository : Could not find assignment nor Assignment repository");
            }

            updateBambooPlanRepository(bambooRepository, bitbucketRepositoryName, bitbucketProjectKey, buildPlanKey, branchName);

            // Overwrite triggers if needed, incl workaround for different repo names, triggered by is present means that the exercise (the BASE build plan) is imported from a
            // previous exercise
            if (optionalTriggeredByRepositories.isPresent() && bambooRepository.getName().equals(OLD_ASSIGNMENT_REPO_NAME)) {
                optionalTriggeredByRepositories = Optional
                        .of(optionalTriggeredByRepositories.get().stream().map(trigger -> trigger.replace(Constants.ASSIGNMENT_REPO_NAME, OLD_ASSIGNMENT_REPO_NAME)).toList());
            }
            optionalTriggeredByRepositories.ifPresent(triggeredByRepositories -> overwriteTriggers(buildPlanKey, triggeredByRepositories));

            log.info("Update plan repository for build plan {} was successful", buildPlanKey);
        }
        catch (Exception e) {
            throw new BambooException(
                    "Something went wrong while updating the template repository of the build plan " + buildPlanKey + " to the student repository : " + e.getMessage(), e);
        }
    }

    /**
     * Update the build plan repository using the cli plugin. This is e.g. invoked, when a student starts a programming exercise.
     * Then the build plan (which was cloned before) needs to be updated to work with the student repository
     *
     * @param bambooRepository the bamboo repository which was obtained before
     * @param bitbucketRepositoryName the name of the new bitbucket repository
     * @param bitbucketProjectKey the key of the corresponding bitbucket project
     * @param buildPlanKey the complete name of the plan
     */
    private void updateBambooPlanRepository(@Nonnull BambooRepositoryDTO bambooRepository, String bitbucketRepositoryName, String bitbucketProjectKey, String buildPlanKey,
            String branchName) {

        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("planKey", buildPlanKey);
        parameters.add("selectedRepository", "com.atlassian.bamboo.plugins.stash.atlassian-bamboo-plugin-stash:stash-rep");
        // IMPORTANT: Don't change the name of the repo! We depend on the naming (assignment, tests) in some other parts of the application
        parameters.add("repositoryName", bambooRepository.getName());
        parameters.add("repositoryId", Long.toString(bambooRepository.getId()));
        parameters.add("confirm", "true");
        parameters.add("save", "Save repository");
        parameters.add("bamboo.successReturnMode", "json");
        parameters.add("repository.stash.branch", branchName);

        BitbucketRepositoryDTO bitbucketRepository = getBitbucketRepository(bitbucketProjectKey, bitbucketRepositoryName);
        parameters.add("repository.stash.repositoryId", bitbucketRepository.id());
        parameters.add("repository.stash.repositorySlug", bitbucketRepository.slug());
        parameters.add("repository.stash.projectKey", bitbucketRepository.project().key());
        parameters.add("repository.stash.repositoryUrl", bitbucketRepository.getCloneSshUrl());

        Optional<ApplicationLinksDTO.ApplicationLinkDTO> applicationLink = getApplicationLink(vcsApplicationLinkName);
        applicationLink.ifPresent(link -> parameters.add("repository.stash.server", link.getId()));

        try {
            String requestUrl = bambooServerUrl + "/chain/admin/config/updateRepository.action";
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestUrl).queryParams(parameters);
            bambooRestTemplate.exchange(builder.build().toUri(), HttpMethod.POST, null, Void.class);
        }
        catch (Exception ex) {
            // TODO: improve error handling
            String message = "Request failed on the server with response code 500. Make sure all required fields have been provided using the various field and value parameters. "
                    + "The server log may provide insight into missing fields: " + ex.getMessage();
            throw new BambooException(message);
        }
    }

    private Optional<ApplicationLinksDTO.ApplicationLinkDTO> getApplicationLink(String applicationLinkName) {
        // first try to find the application link from the local cache
        var cachedLink = findCachedLinkForName(applicationLinkName);
        if (cachedLink.isPresent()) {
            return cachedLink;
        }
        // if there is no local application link available, load them from the Bamboo server
        loadApplicationLinkList();
        return findCachedLinkForName(applicationLinkName);
    }

    private Optional<ApplicationLinksDTO.ApplicationLinkDTO> findCachedLinkForName(String name) {
        return Optional.ofNullable(cachedApplicationLinks.get(name));
    }

    private void loadApplicationLinkList() {
        String requestUrl = bambooServerUrl + "/rest/applinks/latest/applicationlink";
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestUrl).queryParam("expand", "");
        ApplicationLinksDTO links = bambooRestTemplate.exchange(builder.build().toUri(), HttpMethod.GET, null, ApplicationLinksDTO.class).getBody();
        if (links != null && links.getApplicationLinks() != null && !links.getApplicationLinks().isEmpty()) {
            for (var link : links.getApplicationLinks()) {
                if (link.getName() != null) {
                    cachedApplicationLinks.put(link.getName(), link);
                }
            }
        }
    }

    private BitbucketRepositoryDTO getBitbucketRepository(String projectKey, String repositorySlug) {
        String requestUrl = bitbucketServerUrl + "/rest/api/latest/projects/" + projectKey + "/repos/" + repositorySlug;
        return bitbucketRestTemplate.exchange(requestUrl, HttpMethod.GET, null, BitbucketRepositoryDTO.class).getBody();
    }

    /**
     * Fetches the list of repositories in the given build plan, then tries to find the repository in the bamboo build plan, first using the firstRepositoryName,
     * second using the secondRepositoryName
     * @param firstRepositoryName the repository name that should be found first
     * @param secondRepositoryName in case firstRepositoryName is not found, the repository name that should be found second
     * @param buildPlanKey the bamboo build plan key in which the repository with the specified name should be found
     * @return the found bamboo repository in the build plan or null in case the repository with the specified name could not be found
     */
    private BambooRepositoryDTO findBambooRepository(String firstRepositoryName, @Nullable String secondRepositoryName, String buildPlanKey) {
        List<BambooRepositoryDTO> list = this.getBuildPlanRepositoryList(buildPlanKey);
        var bambooRepository = this.lookupRepository(firstRepositoryName, list);
        if (bambooRepository == null && secondRepositoryName != null) {
            bambooRepository = this.lookupRepository(secondRepositoryName, list);
        }
        return bambooRepository;
    }

    private BambooRepositoryDTO lookupRepository(String name, List<BambooRepositoryDTO> list) {
        var repository = list.stream().filter(repo -> name.equals(repo.getName())).findFirst();
        if (repository.isPresent()) {
            return repository.get();
        }

        if (StringUtils.isNumeric(name)) {
            Long id = Long.valueOf(name);
            repository = list.stream().filter(repo -> id.equals(repo.getId())).findFirst();
            if (repository.isPresent()) {
                return repository.get();
            }
        }

        return null;
    }

    private List<BambooRepositoryDTO> getBuildPlanRepositoryList(String buildPlanKey) {
        List<BambooRepositoryDTO> list = new ArrayList<>();
        String requestUrl = bambooServerUrl + "/chain/admin/config/editChainRepository.action";
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestUrl).queryParam("buildKey", buildPlanKey);

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.TEXT_HTML));
        HttpEntity<String> entity = new HttpEntity<>(null, headers);
        String data = bambooRestTemplate.exchange(builder.build().toUri(), HttpMethod.GET, entity, String.class).getBody();

        // the following code finds the required information in the html response
        // it iterates over all data-item-ids in the html code and tries to find the required repository information inside
        // please note: while this approach is not ideal, it works reliably, and there is currently no other API to get the repository id which is needed for other calls to Bamboo
        if (data != null) {
            Pattern findPattern = Pattern.compile("data-item-id=\"(\\d+)\".*\"item-title\">([^<]+)<", Pattern.DOTALL);
            int endIndex = 0;
            int count = 0;
            // use startIndex and endIndex to navigate through the document, try at most 100 times
            while (count < 100) {
                int startIndex = data.indexOf("data-item-id", endIndex);
                endIndex = data.indexOf("</li>", startIndex);
                if (startIndex < 0 || endIndex < 0) {
                    // not found
                    break;
                }
                Matcher matcher = findPattern.matcher(data.substring(startIndex, endIndex));
                if (matcher.find()) {
                    String repositoryId = matcher.group(1).trim();
                    String repositoryName = matcher.group(2).trim();
                    var bambooRepository = new BambooRepositoryDTO(Long.parseLong(repositoryId), repositoryName);
                    list.add(bambooRepository);
                }
                count++;
            }
        }

        return list;
    }

    /**
     * What we basically want to achieve is the following:
     * Tests should NOT trigger the BASE build plan anymore.
     * In old exercises this was the case, but in new exercises, this behavior is not wanted. Therefore, all triggers are removed and the assignment trigger is added again for the
     * BASE build plan
     *
     * This only affects imported exercises and might even not be necessary in case the old BASE build plan was created after December 2019 or was already adapted.
     *
     * @param buildPlanKey the bamboo build plan key (this method is currently only used when importing the BASE build plans)
     * @param triggeredByRepositories a list of triggers (i.e. names of repositories) that should be used to trigger builds in the build plan
     */
    private void overwriteTriggers(String buildPlanKey, final List<String> triggeredByRepositories) {
        try {
            List<BambooTriggerDTO> triggers = getTriggerList(buildPlanKey);
            // Remove all old triggers
            for (final var trigger : triggers) {
                removeTrigger(buildPlanKey, trigger.getId());
            }

            // Add new triggers
            for (final var repositoryName : triggeredByRepositories) {
                addTrigger(buildPlanKey, repositoryName);
            }
        }
        catch (Exception e) {
            throw new BambooException("Unable to overwrite triggers for " + buildPlanKey + "\n" + e.getMessage(), e);
        }
    }

    private List<BambooTriggerDTO> getTriggerList(String buildPlanKey) {
        List<BambooTriggerDTO> list = new ArrayList<>();
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("buildKey", buildPlanKey);

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.TEXT_HTML));
        headers.setContentType(MediaType.TEXT_HTML);
        HttpEntity<String> entity = new HttpEntity<>(null, headers);

        String requestUrl = bambooServerUrl + "/chain/admin/config/editChainTriggers.action";
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestUrl).queryParams(parameters);
        String response = bambooRestTemplate.exchange(builder.build().toUri(), HttpMethod.GET, entity, String.class).getBody();

        if (response != null) {
            Document document = Jsoup.parse(response);

            for (Element element : document.getElementsByClass("item")) {
                Long id = NumberUtils.isCreatable(element.attr("data-item-id")) ? Long.parseLong(element.attr("data-item-id")) : 0L;
                String name = getText(element.getElementsByClass("item-title").first());
                String description = getText(element.getElementsByClass("item-description").first());
                BambooTriggerDTO entry = new BambooTriggerDTO(id, name, description);
                if ("Disabled".equalsIgnoreCase(getText(element.getElementsByClass("lozenge").first()))) {
                    entry.setEnabled(false);
                }
                list.add(entry);
            }
        }

        return list;
    }

    private void addTrigger(String buildPlanKey, String repository) {

        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        BambooRepositoryDTO bambooRepository = findBambooRepository(repository, null, buildPlanKey);
        if (bambooRepository != null) {
            parameters.add("repositoryTrigger", bambooRepository.getIdString());
        }
        parameters.add("planKey", buildPlanKey);
        parameters.add("triggerId", "-1");
        parameters.add("createTriggerKey", "com.atlassian.bamboo.plugins.stash.atlassian-bamboo-plugin-stash:stashTrigger");
        parameters.add("userDescription", null);
        parameters.add("confirm", "true");
        parameters.add("bamboo.successReturnMode", "json");
        parameters.add("decorator", "nothing");

        try {
            String requestUrl = bambooServerUrl + "/chain/admin/config/createChainTrigger.action";
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestUrl).queryParams(parameters);
            bambooRestTemplate.exchange(builder.build().toUri(), HttpMethod.POST, null, Void.class);
        }
        catch (Exception ex) {
            throw new BambooException(
                    ex.getMessage() + ". " + "Missing or invalid parameters for a custom trigger. Most likely cause is an invalid trigger key specified in the type parameter.");
        }
    }

    private void removeTrigger(String buildPlanKey, Long id) {

        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("triggerId", Long.toString(id));
        parameters.add("confirm", "true");
        parameters.add("decorator", "nothing");
        parameters.add("bamboo.successReturnMode", "json");
        parameters.add("planKey", buildPlanKey);

        String requestUrl = bambooServerUrl + "/chain/admin/config/deleteChainTrigger.action";
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestUrl).queryParams(parameters);
        bambooRestTemplate.exchange(builder.build().toUri(), HttpMethod.POST, null, Void.class);
    }

    private static String getText(Element element) {
        return element == null ? "" : element.text();
    }

    public void clearCachedApplicationLinks() {
        cachedApplicationLinks.clear();
    }
}
