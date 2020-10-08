package de.tum.in.www1.artemis.service.connectors.bamboo;

import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
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

        // addRepositoryDetails(bambooRemoteRepository);

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
        // TODO: cache the remote application link
        List<RemoteApplicationLinkDTO> links = getApplicationLinkListInternal();
        return links.stream().filter(link -> url.equalsIgnoreCase(link.getRpcUrl())).findFirst();
    }

    private List<RemoteApplicationLinkDTO> getApplicationLinkListInternal() {

        String requestUrl = bambooServerUrl + "/rest/applinks/latest/applicationlink";
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestUrl).queryParam("expand", "");
        RemoteApplicationLinksDTO links = bambooRestTemplate.exchange(builder.build().toUri(), HttpMethod.GET, null, RemoteApplicationLinksDTO.class).getBody();
        return links.getApplicationLinks();
    }

    // TODO: double check if this is actually needed
    private void addRepositoryDetails(RemoteBambooRepositoryDTO repository) {

        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("planKey", repository.getPlan().getKey());
        parameters.add("repositoryId", repository.getIdString());
        parameters.add("decorator", "nothing");
        parameters.add("confirm", "true");
        String requestUrl = bambooServerUrl + "/chain/admin/config/editRepository.action";
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestUrl).queryParams(parameters);
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.TEXT_HTML));
        HttpEntity<String> entity = new HttpEntity<>(null, headers);
        String data = bambooRestTemplate.exchange(builder.build().toUri(), HttpMethod.GET, entity, String.class).getBody();
        if (data != null && !data.contains("Linked repository")) {
            data = htmlDecode(data);
            Map<String, String> fieldMap = getFields(data, Arrays.asList("userDescription", "taskDisabled"));
            repository.setFields(fieldMap);
            String name = repository.getFieldValue("repository_bitbucket_repository");
            if (name != null && !name.isBlank()) {
                Matcher matcher = Pattern.compile(name + "\\s+\\(([a-zA-Z]+)\\)").matcher(data);
                if (matcher.find()) {
                    String type = matcher.group(1).toUpperCase();
                    if (type.equals("GIT") || type.equals("HG")) {
                        repository.setScmType(matcher.group(1).toUpperCase());
                    }
                }
            }
        }
    }

    private RemoteBitbucketRepositoryDTO getRemoteBitbucketRepository(String projectKey, String repositorySlug) {
        String requestUrl = bitbucketServerUrl + "/rest/api/latest/projects/" + projectKey + "/repos/" + repositorySlug;
        return bitbucketRestTemplate.exchange(requestUrl, HttpMethod.GET, null, RemoteBitbucketRepositoryDTO.class).getBody();
    }

    private RemoteBitbucketProjectDTO getRemoteBitbucketProject(String project) {
        return bitbucketRestTemplate.exchange(bitbucketServerUrl + "/rest/api/latest/projects/" + project, HttpMethod.GET, null, RemoteBitbucketProjectDTO.class).getBody();
    }

    private static String htmlDecode(String string) {
        StringBuffer builder = new StringBuffer();
        Pattern pattern = Pattern.compile("(&[^; ]+?;)");

        Matcher matcher;
        String replacement;
        for (matcher = pattern.matcher(string); matcher.find(); matcher.appendReplacement(builder, Matcher.quoteReplacement(replacement))) {
            String text = matcher.group(1);
            byte elem = -1;
            switch (text.hashCode()) {
                case 1234696:
                    if (text.equals("&gt;")) {
                        elem = 3;
                    }
                    break;
                case 1239501:
                    if (text.equals("&lt;")) {
                        elem = 2;
                    }
                    break;
                case 36187289:
                    if (text.equals("&#38;")) {
                        elem = 1;
                    }
                    break;
                case 36187320:
                    if (text.equals("&#39;")) {
                        elem = 5;
                    }
                    break;
                case 38091805:
                    if (text.equals("&amp;")) {
                        elem = 0;
                    }
                    break;
                case 1180936162:
                    if (text.equals("&apos;")) {
                        elem = 6;
                    }
                    break;
                case 1195861484:
                    if (text.equals("&quot;")) {
                        elem = 4;
                    }
            }

            replacement = switch (elem) {
                case 0, 1 -> "&";
                case 2 -> "<";
                case 3 -> ">";
                case 4 -> "\"";
                case 5, 6 -> "'";
                default -> text;
            };
        }

        matcher.appendTail(builder);
        return builder.toString();
    }

    private static Map<String, String> getFields(String data, List<String> excludeList) {
        Map<String, String> fields = new HashMap<>();
        getTextAndSimilarFields(fields, data);
        getTextareaFields(fields, data);
        getSelectFields(fields, data);

        return fields.entrySet().stream().filter(entry -> !excludeList.contains(entry.getKey())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public static void getTextAndSimilarFields(Map<String, String> fields, String data) {
        String regex = "(?s)(?i)(<input.+?type=\"((?:text)|(?:checkbox))\".*?name=\"(.*?)\".+?value=\"(.*?)\".*?/>)";

        String key;
        String value;
        for (Matcher matcher = Pattern.compile(regex).matcher(data); matcher.find(); fields.put(key, value)) {
            String type = matcher.group(2).toLowerCase();
            key = matcher.group(3);
            value = matcher.group(4);
            if ("checkbox".equals(type)) {
                String all = matcher.group(1);
                if (!all.contains("\"checked\"")) {
                    value = "";
                }
            }
        }
    }

    private static void getTextareaFields(Map<String, String> fields, String data) {
        String regex = "(?s)(?i)<textarea.+?name=\"(.*?)\".*?>(.*?)</textarea>";
        Matcher matcher = Pattern.compile(regex).matcher(data);

        while (matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(2);
            fields.put(key, value);
        }
    }

    private static void getSelectFields(Map<String, String> fields, String data) {
        String regex = "(?s)(?i)<select.+?id=\"(.*?)\".*?>(.*?)</select>";
        String regexOptions = "(?s)(?i)<option.+?value=\"(.*?)\".*?(.*?)</option>";
        Pattern patternOptions = Pattern.compile(regexOptions);

        String key;
        StringBuilder builder;
        for (Matcher matcher = Pattern.compile(regex).matcher(data); matcher.find(); fields.put(key, builder.toString())) {
            key = matcher.group(1);
            builder = new StringBuilder();
            String options = matcher.group(2);
            Matcher matcherOptions = patternOptions.matcher(options);
            String firstValue = null;

            while (matcherOptions.find()) {
                if (firstValue == null) {
                    firstValue = matcherOptions.group(1);
                }

                if (matcherOptions.group(2).contains("selected=\"selected\"")) {
                    if (builder.length() > 0) {
                        builder.append("Constants.COMMA");
                    }

                    builder.append(matcherOptions.group(1));
                }
            }

            if (builder.length() == 0) {
                builder.append(firstValue);
            }
        }
    }
}
