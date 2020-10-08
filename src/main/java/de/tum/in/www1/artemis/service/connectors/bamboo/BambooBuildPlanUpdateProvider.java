package de.tum.in.www1.artemis.service.connectors.bamboo;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.appfire.bamboo.cli.BambooClient;
import com.appfire.bitbucket.cli.BitbucketClient;
import com.appfire.bitbucket.cli.objects.RemoteProject;
import com.appfire.bitbucket.cli.objects.RemoteRepository;
import com.appfire.bitbucket.cli.requesthelpers.PagedRequestHandler;
import com.appfire.bitbucket.cli.requesthelpers.RequestHelper;
import com.appfire.common.cli.CliClient;
import com.appfire.common.cli.CliUtils;
import com.appfire.common.cli.FieldUtilities;
import com.appfire.common.cli.JsonUtils;
import com.appfire.common.cli.objects.RemoteApplicationLink;
import com.appfire.common.cli.requesthelpers.DefaultRequestHelper;

@Component
@Profile("bamboo")
public class BambooBuildPlanUpdateProvider {

    private final BambooClient bambooClient;

    private final BitbucketClient bitbucketClient;

    public BambooBuildPlanUpdateProvider(BambooClient bambooClient, BitbucketClient bitbucketClient) {
        this.bambooClient = bambooClient;
        this.bitbucketClient = bitbucketClient;
    }

    /**
     * Update the build plan repository using the cli plugin. This is e.g. invoked, when a student starts a programming exercise.
     * Then the build plan (which was cloned before) needs to be updated to work with the student repository
     *
     * @param bambooRemoteRepository the remote bamboo repository which was obtained before
     * @param bitbucketRepositoryName the name of the new bitbucket repository
     * @param bitbucketProjectKey the key of the corresponding bitbucket project
     * @param completePlanName the complete name of the plan
     * @throws CliClient.ClientException a client exception
     * @throws CliClient.RemoteRestException a server exception
     */
    public void updateRepository(@Nonnull com.appfire.bamboo.cli.objects.RemoteRepository bambooRemoteRepository, String bitbucketRepositoryName, String bitbucketProjectKey,
            String completePlanName) throws CliClient.ClientException, CliClient.RemoteRestException {

        Map<String, String> parameters = new HashMap<>();
        parameters.put("planKey", completePlanName);

        addRepositoryDetails(bambooRemoteRepository);

        parameters.put("selectedRepository", "com.atlassian.bamboo.plugins.stash.atlassian-bamboo-plugin-stash:stash-rep");
        // IMPORTANT: Don't change the name of the repo! We depend on the naming (assignment, tests) in some other parts of the application
        parameters.put("repositoryName", bambooRemoteRepository.getName());
        parameters.put("repositoryId", Long.toString(bambooRemoteRepository.getId()));
        parameters.put("confirm", "true");
        parameters.put("save", "Save repository");
        parameters.put("bamboo.successReturnMode", "json");
        parameters.put("repository.stash.branch", "master");

        com.appfire.bitbucket.cli.objects.RemoteRepository bitbucketRepository;
        try {
            bitbucketRepository = getRemoteRepository(bitbucketProjectKey, bitbucketRepositoryName, true);
        }
        catch (CliClient.ClientException | CliClient.RemoteRestException ex) {
            throw new CliClient.ClientException("Bitbucket failed trying to get repository details: " + ex.getMessage());
        }

        RemoteApplicationLink link = bitbucketClient.getApplicationLink();
        if (link == null) {
            link = getApplicationLink(bambooClient.getString("targetServer"));
        }

        if (link != null) {
            parameters.put("repository.stash.server", link.getId());
        }

        parameters.put("repository.stash.repositoryId", bitbucketRepository.getIdString());
        parameters.put("repository.stash.repositorySlug", bitbucketRepository.getSlug());
        parameters.put("repository.stash.projectKey", bitbucketRepository.getProject());
        parameters.put("repository.stash.repositoryUrl", bitbucketRepository.getCloneSshUrl());

        String responseData = "";

        try {

            DefaultRequestHelper helper = bambooClient.getPseudoRequestHelper();
            helper.setRequestType(DefaultRequestHelper.RequestType.POST);
            helper.setContentType(DefaultRequestHelper.RequestContentType.JSON);
            helper.setParameters(parameters);
            helper.makeRequest("/chain/admin/config/updateRepository.action");
            responseData = helper.getResponseData();

        }
        catch (CliClient.RemoteInternalServerErrorException ex) {
            String message = "Request failed on the server with response code 500. Make sure all required fields have been provided using the various field and value parameters. "
                    + "The server log may provide insight into missing fields: " + ex.getMessage();
            throw new CliClient.ClientException(message);
        }

        JSONObject json = bambooClient.getJsonWithVerboseLogging(responseData);
        JSONObject repositoryJson = JsonUtils.getJsonOrNull(JsonUtils.getStringOrNull(json, "repositoryResult"));
        if (repositoryJson == null) {
            String error = checkForError(responseData);
            throw new CliClient.ClientException(error.equals("") ? "Unknown error occurred." : error);
        }
    }

    private RemoteApplicationLink getApplicationLink(String name) throws CliClient.ClientException, CliClient.RemoteRestException {
        return RemoteApplicationLink.findByNameOrUrl(name, this.getApplicationLinkListInternal());
    }

    private List<RemoteApplicationLink> getApplicationLinkListInternal() throws CliClient.ClientException, CliClient.RemoteRestException {
        this.setRequestType(DefaultRequestHelper.RequestType.GET);
        this.setAcceptContentType(DefaultRequestHelper.RequestContentType.JSON);
        this.makeStandardRequest(RemoteApplicationLink.getRequest());

        List<JSONObject> links = JsonUtils.getJsonArray(this.getResponseJson(), "applicationLinks");
        return links.stream().map(RemoteApplicationLink::new).collect(Collectors.toList());
    }

    private com.appfire.bamboo.cli.objects.RemoteRepository addRepositoryDetails(com.appfire.bamboo.cli.objects.RemoteRepository repository)
            throws CliClient.ClientException, CliClient.RemoteRestException {
        boolean isPlanRepository = repository.isPlanRepository();
        Map<String, String> parameters = new HashMap();
        if (isPlanRepository) {
            parameters.put("planKey", repository.getPlan().getKey());
        }

        parameters.put("repositoryId", repository.getIdString());
        parameters.put("decorator", "nothing");
        parameters.put("confirm", "true");
        DefaultRequestHelper helper = this.bambooClient.getPseudoRequestHelper();
        helper.setParameters(parameters);
        helper.makeRequest(isPlanRepository ? "/chain/admin/config/editRepository.action" : "/admin/editLinkedRepository.action");
        String[] excludeList = new String[] { "userDescription", "taskDisabled" };
        String data = helper.getResponseData();
        if (!isPlanRepository || !data.contains("Linked repository")) {
            data = CliUtils.htmlDecode(data);
            Map<String, String> fieldMap = FieldUtilities.getFields(data, excludeList, this.bambooClient.getInteger("outputFormat") != 999);
            repository.setFields(fieldMap);
            String name = repository.getFieldValue("repository_bitbucket_repository");
            if (StringUtils.isNotBlank(name)) {
                Matcher matcher = Pattern.compile(name + "\\s+\\(([a-zA-Z]+)\\)").matcher(data);
                if (matcher.find()) {
                    String type = matcher.group(1).toUpperCase();
                    if (type.equals("GIT") || type.equals("HG")) {
                        repository.setScmType(matcher.group(1).toUpperCase());
                    }
                }
            }
        }

        return repository;
    }

    public RemoteRepository getRemoteRepository(String project, String repository, boolean throwException) throws CliClient.RemoteRestException, CliClient.ClientException {
        RemoteRepository remoteRepository = null;
        if (project.equals("@all")) {
            List<RemoteRepository> list = this.getRepositoryListInternal(project, 2, Pattern.compile(repository, 16));
            if (list.size() == 1) {
                remoteRepository = (RemoteRepository) list.get(0);
            }
            else if (list.size() > 1) {
                throw new CliClient.ClientException("More than one repository matching " + CliUtils.quoteString(repository) + " was found.");
            }
        }
        else {
            RemoteProject remoteProject = this.bitbucketClient.getProjectHelper().getRemoteProject(project, true);
            RequestHelper helper = this.bitbucketClient.getRequestHelper();

            try {
                helper.setShouldPrintErrorMessages(false);
                helper.makeStandardRequest(RemoteRepository.getRequest(project, repository));
                remoteRepository = new RemoteRepository(helper.getResponseJsonLegacy());
            }
            catch (CliClient.RemoteResourceNotFoundException var12) {
            }
            catch (CliClient.RemoteRestException var13) {
                helper.printErrorMessages(throwException);
            }
            finally {
                helper.setShouldPrintErrorMessages(true);
            }

            if (remoteRepository == null) {
                remoteRepository = this.getRemoteRepositoryLookup(remoteProject, repository, throwException);
            }
        }

        if (throwException && remoteRepository == null) {
            throw new CliClient.ClientException("Repository " + CliUtils.quoteString(repository) + " not found.");
        }
        else {
            return remoteRepository;
        }
    }

    private List<RemoteRepository> getRepositoryListInternal(String project, int limit, Pattern pattern) throws CliClient.ClientException, CliClient.RemoteRestException {
        Object list;
        if (project.equals("@all")) {
            list = new ArrayList();
            List<RemoteProject> projectList = this.bitbucketClient.getProjectHelper().getProjectListInternal((String) null, 2147483647, (Pattern) null);
            int newLimit = limit;
            Iterator var7 = projectList.iterator();

            while (var7.hasNext()) {
                RemoteProject remoteProject = (RemoteProject) var7.next();
                ((List) list).addAll(this.getRepositoryList(remoteProject, newLimit, pattern));
                newLimit = limit - ((List) list).size();
                if (newLimit <= 0) {
                    break;
                }
            }
        }
        else {
            RemoteProject remoteProject = this.bitbucketClient.getProjectHelper().getRemoteProject(project, true);
            list = this.getRepositoryList(remoteProject, limit, pattern);
        }

        return (List) list;
    }

    private String getRepositoryList(String project, int limit, Pattern pattern) throws CliClient.ClientException, CliClient.RemoteRestException {
        List<RemoteRepository> list = this.getRepositoryListInternal(project, limit, pattern);
        StringBuilder builder = new StringBuilder();
        if (!this.bitbucketClient.isExistingFileWithAppend()) {
            RemoteRepository.appendCsvHeader(builder);
            builder.append('\n');
        }

        Iterator var6 = list.iterator();

        while (var6.hasNext()) {
            RemoteRepository entry = (RemoteRepository) var6.next();
            entry.appendCsv(builder);
        }

        String message = list.size() + " repositories in list";
        return this.bitbucketClient.standardFinishNew(message, list.size() > 0, builder);
    }

    private List<RemoteRepository> getRepositoryList(RemoteProject remoteProject, int limit, Pattern pattern) throws CliClient.ClientException, CliClient.RemoteRestException {
        List<RemoteRepository> repositoryList = new ArrayList();
        PagedRequestHandler handler = new PagedRequestHandler(bitbucketClient, RemoteRepository.getRequestBase(remoteProject.getKey()), (Map) null);
        int count = 0;

        while (handler.hasNext()) {
            JSONObject jsonResponse = handler.getNext();
            List<JSONObject> list = (List) jsonResponse.get("values");
            Iterator var9 = list.iterator();

            while (var9.hasNext()) {
                JSONObject json = (JSONObject) var9.next();
                RemoteRepository remoteRepository = new RemoteRepository(json);
                if (pattern == null || pattern.matcher(remoteRepository.getName()).matches()) {
                    repositoryList.add(remoteRepository);
                    ++count;
                }

                if (count >= limit) {
                    break;
                }
            }

            if (count >= limit) {
                break;
            }
        }

        Collections.sort(repositoryList, Comparator.comparing(RemoteRepository::getName));
        return repositoryList;
    }

    private RemoteRepository getRemoteRepositoryLookup(RemoteProject remoteProject, String repository, boolean throwException)
            throws CliClient.RemoteRestException, CliClient.ClientException {
        RemoteRepository remoteRepository = null;
        boolean isId = CliUtils.isNumeric(repository);
        PagedRequestHandler handler = new PagedRequestHandler(bitbucketClient, RemoteRepository.getRequestBase(remoteProject.getKey()), (Map) null);
        boolean done = false;

        label40: while (handler.hasNext() && !done) {
            JSONObject jsonResponse = handler.getNext();
            List<JSONObject> list = (JSONArray) jsonResponse.get("values");
            Iterator var10 = list.iterator();

            JSONObject json;
            do {
                if (!var10.hasNext()) {
                    continue label40;
                }

                json = (JSONObject) var10.next();
            }
            while ((!isId || !JsonUtils.getString(json, "id").equals(repository)) && (isId || !JsonUtils.getString(json, "name").equalsIgnoreCase(repository)));

            remoteRepository = new RemoteRepository(json);
            done = true;
        }

        if (throwException && remoteRepository == null) {
            throw new CliClient.RemoteRestException("Repository: " + repository + " not found.");
        }
        else {
            return remoteRepository;
        }
    }

    /**
     * This method was taken from RepositoryHelper of the Bamboo CLI Plugin
     * @param data the response from the server
     * @return an error message
     * @see com.appfire.bamboo.cli.helpers.RepositoryHelper
     */
    private String checkForError(String data) {
        String message = CliUtils.matchRegex(data, "(?s)<div[^>]*class=\"aui-message error\">\\s+<p>([^<]*)<").trim();

        String regex = "<div[^>]*class=\"error(?: control-form-error){0,1}\"[^>]*data-field-name=\"([^\"]+)\"[^<]*>([^<]*)</div>";
        Pattern pattern = Pattern.compile(regex, 2);
        Matcher matcher = pattern.matcher(data);
        if (matcher.find()) {
            String field = matcher.group(1);
            message = CliUtils.endWithPeriod(matcher.group(2)) + (field == null ? "" : " Error field is " + field + ".");
        }

        if (message.equals("")) {
            message = CliUtils.matchRegex(data, "<div[^>]*class=\"error(?: control-form-error){0,1}\"[^>]*>([^<]*)</div>").trim();
        }

        if (message.equals("")) {
            message = CliUtils.matchRegex(data, "error[^>]*>\\s*<p class=\"title\"[^>]*>([^<]*)<").trim();
        }

        return message;
    }
}
