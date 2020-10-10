package de.tum.in.www1.artemis.service.connectors.bamboo;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import de.tum.in.www1.artemis.exception.BambooException;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.RemoteApplicationLinksDTO;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.RemoteApplicationLinksDTO.RemoteApplicationLinkDTO;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.RemoteBambooRepositoryDTO;
import de.tum.in.www1.artemis.service.connectors.bitbucket.dto.RemoteBitbucketProjectDTO;
import de.tum.in.www1.artemis.service.connectors.bitbucket.dto.RemoteBitbucketRepositoryDTO;

@Component
@Profile("bamboo")
public class BambooBuildPlanUpdateProvider {

    @Value("${artemis.version-control.url}")
    private URL bitbucketServerUrl;

    @Value("${artemis.continuous-integration.url}")
    private URL bambooServerUrl;

    private final RestTemplate bambooRestTemplate;

    private final RestTemplate bitbucketRestTemplate;

    private List<RemoteApplicationLinkDTO> cachedApplicationLinks = new ArrayList<>();

    public BambooBuildPlanUpdateProvider(@Qualifier("bambooRestTemplate") RestTemplate bambooRestTemplate, @Qualifier("bitbucketRestTemplate") RestTemplate bitbucketRestTemplate) {
        this.bambooRestTemplate = bambooRestTemplate;
        this.bitbucketRestTemplate = bitbucketRestTemplate;
    }

    /**
     * Update the build plan repository using the cli plugin. This is e.g. invoked, when a student starts a programming exercise.
     * Then the build plan (which was cloned before) needs to be updated to work with the student repository
     *
     * @param bambooRemoteRepository the remote bamboo repository which was obtained before
     * @param bitbucketRepositoryName the name of the new bitbucket repository
     * @param bitbucketProjectKey the key of the corresponding bitbucket project
     * @param completePlanName the complete name of the plan
     */
    public void updateRepository(@Nonnull RemoteBambooRepositoryDTO bambooRemoteRepository, String bitbucketRepositoryName, String bitbucketProjectKey, String completePlanName) {

        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("planKey", completePlanName);

        parameters.add("selectedRepository", "com.atlassian.bamboo.plugins.stash.atlassian-bamboo-plugin-stash:stash-rep");
        // IMPORTANT: Don't change the name of the repo! We depend on the naming (assignment, tests) in some other parts of the application
        parameters.add("repositoryName", bambooRemoteRepository.getName());
        parameters.add("repositoryId", Long.toString(bambooRemoteRepository.getId()));
        parameters.add("confirm", "true");
        parameters.add("save", "Save repository");
        parameters.add("bamboo.successReturnMode", "json");
        parameters.add("repository.stash.branch", "master");

        RemoteBitbucketRepositoryDTO bitbucketRepository = getRemoteBitbucketRepository(bitbucketProjectKey, bitbucketRepositoryName);
        Optional<RemoteApplicationLinkDTO> link = getApplicationLink(bitbucketServerUrl.toString());
        link.ifPresent(remoteApplicationLink -> parameters.add("repository.stash.server", remoteApplicationLink.getId()));

        parameters.add("repository.stash.repositoryId", bitbucketRepository.getId());
        parameters.add("repository.stash.repositorySlug", bitbucketRepository.getSlug());
        parameters.add("repository.stash.projectKey", bitbucketRepository.getProject().getKey());
        parameters.add("repository.stash.repositoryUrl", bitbucketRepository.getCloneSshUrl());

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

    private Optional<RemoteApplicationLinkDTO> getApplicationLink(String url) {
        // first try to find the application link from the local cache
        var cachedLink = findCachedLinkForUrl(url);
        if (cachedLink.isPresent()) {
            return cachedLink;
        }
        // if there is no local application link available, load them Bamboo server
        cachedApplicationLinks = loadApplicationLinkList();
        return findCachedLinkForUrl(url);
    }

    private Optional<RemoteApplicationLinkDTO> findCachedLinkForUrl(String url) {
        return cachedApplicationLinks.stream().filter(link -> url.equalsIgnoreCase(link.getRpcUrl())).findFirst();
    }

    private List<RemoteApplicationLinkDTO> loadApplicationLinkList() {
        String requestUrl = bambooServerUrl + "/rest/applinks/latest/applicationlink";
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestUrl).queryParam("expand", "");
        RemoteApplicationLinksDTO links = bambooRestTemplate.exchange(builder.build().toUri(), HttpMethod.GET, null, RemoteApplicationLinksDTO.class).getBody();
        if (links != null) {
            return links.getApplicationLinks();
        }
        else {
            return List.of();
        }
    }

    private RemoteBitbucketRepositoryDTO getRemoteBitbucketRepository(String projectKey, String repositorySlug) {
        String requestUrl = bitbucketServerUrl + "/rest/api/latest/projects/" + projectKey + "/repos/" + repositorySlug;
        return bitbucketRestTemplate.exchange(requestUrl, HttpMethod.GET, null, RemoteBitbucketRepositoryDTO.class).getBody();
    }

    // this method is currently not needed but might be useful in the future
    private RemoteBitbucketProjectDTO getRemoteBitbucketProject(String project) {
        return bitbucketRestTemplate.exchange(bitbucketServerUrl + "/rest/api/latest/projects/" + project, HttpMethod.GET, null, RemoteBitbucketProjectDTO.class).getBody();
    }
}
