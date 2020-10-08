package de.tum.in.www1.artemis.service.connectors;

import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
import de.tum.in.www1.artemis.service.connectors.bamboo.BambooBuildPlanUpdateProvider;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.RemoteBambooRepositoryDTO;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.RemotePlanDTO;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.RemoteTriggerDTO;

@Service
// Only activate this service bean, if both Bamboo and Bitbucket are activated (@Profile({"bitbucket","bamboo"}) would activate
// this if any profile is active (OR). We want both (AND)
@Profile("bamboo & bitbucket")
public class BitbucketBambooUpdateService implements ContinuousIntegrationUpdateService {

    @Value("${artemis.continuous-integration.url}")
    private URL bambooServerUrl;

    private static final String OLD_ASSIGNMENT_REPO_NAME = "Assignment";

    private final Logger log = LoggerFactory.getLogger(BitbucketBambooUpdateService.class);

    private final BambooBuildPlanUpdateProvider bambooBuildPlanUpdateProvider;

    private final RestTemplate restTemplate;

    public BitbucketBambooUpdateService(BambooBuildPlanUpdateProvider bambooBuildPlanUpdateProvider, @Qualifier("bambooRestTemplate") RestTemplate restTemplate) {
        this.bambooBuildPlanUpdateProvider = bambooBuildPlanUpdateProvider;
        this.restTemplate = restTemplate;
    }

    @Override
    public void updatePlanRepository(String bambooProject, String planKey, String bambooRepositoryName, String bitbucketProject, String bitbucketRepository,
            Optional<List<String>> triggeredBy) {
        try {
            log.debug("Update plan repository for build plan " + planKey);
            RemoteBambooRepositoryDTO bambooRemoteRepository = getRemoteRepository(bambooRepositoryName, OLD_ASSIGNMENT_REPO_NAME, planKey);
            if (bambooRemoteRepository == null) {
                throw new BambooException("Something went wrong while updating the template repository of the build plan " + planKey
                        + " to the student repository : Could not find assignment nor Assignment repository");
            }

            bambooBuildPlanUpdateProvider.updateRepository(bambooRemoteRepository, bitbucketRepository, bitbucketProject, planKey);

            // Overwrite triggers if needed, incl workaround for different repo names, triggered by is present means that the exercise (the BASE build plan) is imported from a
            // previous exercise
            if (triggeredBy.isPresent() && bambooRemoteRepository.getName().equals(OLD_ASSIGNMENT_REPO_NAME)) {
                triggeredBy = Optional
                        .of(triggeredBy.get().stream().map(trigger -> trigger.replace(Constants.ASSIGNMENT_REPO_NAME, OLD_ASSIGNMENT_REPO_NAME)).collect(Collectors.toList()));
            }
            triggeredBy.ifPresent(repoTriggers -> overwriteTriggers(planKey, repoTriggers));

            log.info("Update plan repository for build plan " + planKey + " was successful");
        }
        catch (Exception e) {
            throw new BambooException("Something went wrong while updating the template repository of the build plan " + planKey + " to the student repository : " + e.getMessage(),
                    e);
        }
    }

    private RemoteBambooRepositoryDTO getRemoteRepository(String repositoryName, String oldRepositoryName, String plan) {
        List<RemoteBambooRepositoryDTO> list = this.getRemoteRepositoryList(plan);
        var bambooRepository = this.lookupRepository(repositoryName, list);
        if (bambooRepository == null && oldRepositoryName != null) {
            return this.lookupRepository(oldRepositoryName, list);
        }
        return null;
    }

    protected RemoteBambooRepositoryDTO lookupRepository(String name, List<RemoteBambooRepositoryDTO> list) {
        var remoteRepository = list.stream().filter(repository -> name.equals(repository.getName())).findFirst();
        if (remoteRepository.isPresent()) {
            return remoteRepository.get();
        }

        if (StringUtils.isNumeric(name)) {
            Long id = Long.valueOf(name);
            remoteRepository = list.stream().filter(repository -> id.equals(repository.getId())).findFirst();
            if (remoteRepository.isPresent()) {
                return remoteRepository.get();
            }
        }

        return null;
    }

    private List<RemoteBambooRepositoryDTO> getRemoteRepositoryList(String plan) {
        RemotePlanDTO remotePlan = this.getRemotePlan(plan);
        return this.getRemoteRepositoryList(remotePlan);
    }

    private RemotePlanDTO getRemotePlan(String planName) {
        String requestUrl = bambooServerUrl + "/rest/api/latest/plan/" + planName;
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestUrl).queryParam("expand", "");
        return restTemplate.exchange(builder.build().toUri(), HttpMethod.GET, null, RemotePlanDTO.class).getBody();
    }

    private List<RemoteBambooRepositoryDTO> getRemoteRepositoryList(RemotePlanDTO plan) {
        List<RemoteBambooRepositoryDTO> list = new ArrayList<>();
        String requestUrl = bambooServerUrl + "/chain/admin/config/editChainRepository.action";
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestUrl).queryParam("buildKey", plan.getKey());

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.TEXT_HTML));
        HttpEntity<String> entity = new HttpEntity<>(null, headers);
        String data = restTemplate.exchange(builder.build().toUri(), HttpMethod.GET, entity, String.class).getBody();

        // the following code finds the required information in the html response
        if (data != null) {
            int count = 0;
            Pattern findPattern = Pattern.compile("data-item-id=\"(\\d+)\".*\"item-title\">([^<]+)<", 32);
            int endIndex = 0;

            do {
                int startIndex = data.indexOf("data-item-id", endIndex);
                if (startIndex < 0) {
                    break;
                }

                endIndex = data.indexOf("</li>", startIndex);
                Matcher matcher = findPattern.matcher(data.substring(startIndex, endIndex));
                if (matcher.find()) {
                    String id = matcher.group(1).trim();
                    String name = matcher.group(2).trim();
                    ++count;
                    list.add(new RemoteBambooRepositoryDTO(plan, Long.parseLong(id), name));
                }
            }
            while (count < 2147483647);
        }

        return list;
    }

    /**
     * What we basically want to achieve is the following:
     * Tests should NOT trigger the BASE build plan any more.
     * In old exercises this was the case, but in new exercises, this behavior is not wanted. Therefore, all triggers are removed and the assignment trigger is added again for the
     * BASE build plan
     *
     * This only affects imported exercises and might even not be necessary in case the old BASE build plan was created after December 2019 or was already adapted.
     *
     * @param planKey the bamboo plan key (this is currently only used for BASE build plans
     * @param triggeredBy a list of triggers (i.e. names of repositories) that should be used to trigger builds in the build plan
     */
    private void overwriteTriggers(final String planKey, final List<String> triggeredBy) {
        try {
            List<RemoteTriggerDTO> remoteTriggers = getTriggerList(planKey);
            // Remove all old triggers
            for (final var trigger : remoteTriggers) {
                removeTrigger(planKey, trigger.getId());
            }

            // Add new triggers
            for (final var repo : triggeredBy) {
                addTrigger(planKey, repo);
            }
        }
        catch (Exception e) {
            throw new BambooException("Unable to overwrite triggers for " + planKey + "\n" + e.getMessage(), e);
        }
    }

    private List<RemoteTriggerDTO> getTriggerList(String plan) {
        RemotePlanDTO remotePlan = getRemotePlan(plan);
        return this.getRemoteTriggerList(remotePlan);
    }

    private void removeTrigger(String plan, Long id) {
        RemotePlanDTO remotePlan = getRemotePlan(plan);

        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("triggerId", Long.toString(id));
        parameters.add("confirm", "true");
        parameters.add("decorator", "nothing");
        parameters.add("bamboo.successReturnMode", "json");
        parameters.add("planKey", remotePlan.getKey());

        String requestUrl = bambooServerUrl + "/chain/admin/config/deleteChainTrigger.action";
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestUrl).queryParams(parameters);
        restTemplate.exchange(builder.build().toUri(), HttpMethod.POST, null, Map.class);
    }

    private List<RemoteTriggerDTO> getRemoteTriggerList(RemotePlanDTO remotePlan) {
        List<RemoteTriggerDTO> list = new ArrayList<>();
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("buildKey", remotePlan.getKey());

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.TEXT_HTML));
        headers.setContentType(MediaType.TEXT_HTML);
        HttpEntity<String> entity = new HttpEntity<>(null, headers);

        String requestUrl = bambooServerUrl + "/chain/admin/config/editChainTriggers.action";
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestUrl).queryParams(parameters);
        String response = restTemplate.exchange(builder.build().toUri(), HttpMethod.GET, entity, String.class).getBody();

        Document document = Jsoup.parse(response);

        for (Element element : document.getElementsByClass("item")) {
            Long id = NumberUtils.isCreatable(element.attr("data-item-id")) ? Long.parseLong(element.attr("data-item-id")) : 0L;
            String name = getText(element.getElementsByClass("item-title").first());
            String description = getText(element.getElementsByClass("item-description").first());
            RemoteTriggerDTO entry = new RemoteTriggerDTO(remotePlan, id, name, description);
            if ("Disabled".equalsIgnoreCase(getText(element.getElementsByClass("lozenge").first()))) {
                entry.setEnabled(false);
            }
            list.add(entry);
        }

        return list;
    }

    private static String getText(Element element) {
        return element == null ? "" : element.text();
    }

    private void addTrigger(String plan, String repository) {
        RemotePlanDTO remotePlan = getRemotePlan(plan);

        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("repositoryTrigger", getRemoteRepository(repository, null, remotePlan.getKey()).getIdString());
        parameters.add("planKey", remotePlan.getKey());
        parameters.add("triggerId", "-1");
        parameters.add("createTriggerKey", "com.atlassian.bamboo.plugins.stash.atlassian-bamboo-plugin-stash:stashTrigger");
        parameters.add("userDescription", null);
        parameters.add("confirm", "true");
        parameters.add("bamboo.successReturnMode", "json");
        parameters.add("decorator", "nothing");

        try {
            String requestUrl = bambooServerUrl + "/chain/admin/config/createChainTrigger.action";
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestUrl).queryParams(parameters);
            restTemplate.exchange(builder.build().toUri(), HttpMethod.POST, null, Map.class);
        }
        catch (Exception ex) {
            throw new BambooException(ex.getMessage() + ". "
                    + "Missing or invalid parameters for a custom trigger. Most likely cause is an invalid trigger key specified in the type parameter. Specify -v on the action and look in the detailed information for problem determination information.");
        }
    }
}
