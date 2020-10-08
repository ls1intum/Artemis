package de.tum.in.www1.artemis.service.connectors;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.appfire.bamboo.cli.BambooClient;
import com.appfire.bamboo.cli.helpers.TriggerHelper;
import com.appfire.bamboo.cli.objects.*;
import com.appfire.bamboo.cli.requesthelpers.PseudoRequestHelper;
import com.appfire.bamboo.cli.requesthelpers.RequestHelper;
import com.appfire.common.cli.*;
import com.appfire.common.cli.requesthelpers.DefaultRequestHelper;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.exception.BambooException;
import de.tum.in.www1.artemis.service.connectors.bamboo.BambooBuildPlanUpdateProvider;

@Service
// Only activate this service bean, if both Bamboo and Bitbucket are activated (@Profile({"bitbucket","bamboo"}) would activate
// this if any profile is active (OR). We want both (AND)
@Profile("bamboo & bitbucket")
public class BitbucketBambooUpdateService implements ContinuousIntegrationUpdateService {

    private static final String OLD_ASSIGNMENT_REPO_NAME = "Assignment";

    private final Logger log = LoggerFactory.getLogger(BitbucketBambooUpdateService.class);

    private final BambooClient bambooClient;

    private final BambooBuildPlanUpdateProvider bambooBuildPlanUpdateProvider;

    public BitbucketBambooUpdateService(BambooClient bambooClient, BambooBuildPlanUpdateProvider bambooBuildPlanUpdateProvider) {
        this.bambooClient = bambooClient;
        this.bambooBuildPlanUpdateProvider = bambooBuildPlanUpdateProvider;
    }

    @Override
    public void updatePlanRepository(String bambooProject, String planKey, String bambooRepositoryName, String bitbucketProject, String bitbucketRepository,
            Optional<List<String>> triggeredBy) {
        try {
            log.debug("Update plan repository for build plan " + planKey);
            com.appfire.bamboo.cli.objects.RemoteRepository bambooRemoteRepository = getRemoteRepository(bambooRepositoryName, planKey);
            // Workaround for old exercises which used a different repositoryName
            if (bambooRemoteRepository == null) {
                bambooRemoteRepository = getRemoteRepository(OLD_ASSIGNMENT_REPO_NAME, planKey);
                if (bambooRemoteRepository == null) {
                    throw new BambooException("Something went wrong while updating the template repository of the build plan " + planKey
                            + " to the student repository : Could not find assignment nor Assignment repository");
                }
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
        catch (CliClient.ClientException | CliClient.RemoteRestException e) {
            throw new BambooException("Something went wrong while updating the template repository of the build plan " + planKey + " to the student repository : " + e.getMessage(),
                    e);
        }
    }

    private RemoteRepository getRemoteRepository(String repository, String plan) throws CliClient.RemoteRestException, CliClient.ClientException {
        List<RemoteRepository> list = this.getRemoteRepositoryList(plan);
        return this.lookupRepository(repository, list);
    }

    protected RemoteRepository lookupRepository(String name, List<RemoteRepository> list) {
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

    private List<RemoteRepository> getRemoteRepositoryList(String plan) throws CliClient.ClientException, CliClient.RemoteRestException {
        RemotePlan remotePlan = StringUtils.isBlank(plan) ? null : this.getRemotePlan(plan);
        return this.getRemoteRepositoryList(remotePlan);
    }

    private RemotePlan getRemotePlan(String plan) throws CliClient.ClientException, CliClient.RemoteRestException {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("expand", "");
        RequestHelper helper = new RequestHelper(bambooClient);

        try {
            helper.setParameters(parameters);
            helper.makeStandardRequest(RemotePlan.getRequest(plan));
        }
        catch (CliClient.RemoteResourceNotFoundException var9) {
            return null;
        }

        return new RemotePlan(helper.getResponseJson());
    }

    private List<RemoteRepository> getRemoteRepositoryList(RemotePlan plan) throws CliClient.ClientException, CliClient.RemoteRestException {
        List<RemoteRepository> list = new ArrayList<>();
        boolean isPlan = plan != null;
        String request;
        if (isPlan) {
            request = "/chain/admin/config/editChainRepository.action";
        }
        else {
            String actionName = "default.action";
            String qualifier = "Global";
            request = "/admin/configure" + qualifier + "Repositories!" + actionName;
        }

        Map<String, String> parameters = new HashMap<>();
        if (isPlan) {
            parameters.put("buildKey", plan.getKey());
        }

        DefaultRequestHelper helper = new PseudoRequestHelper(bambooClient);
        helper.setParameters(parameters);
        helper.makeRequest(request);
        String data = helper.getResponseData();
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
                    list.add(new RemoteRepository(plan, Long.parseLong(id), name));
                }
            }
            while (count < 2147483647);
        }

        return list;
    }

    /**
     * What we basically want to achieve is the following.
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
            // TODO: it should be simpler to get the ids
            final var triggersString = getTriggerList(planKey, 99, Pattern.compile(".*"));
            // Bamboo CLI returns a weird String, which is the reason for this way of parsing it
            final var oldTriggers = Arrays.stream(triggersString.split("\n")).map(trigger -> trigger.replace("\"", "").split(","))
                    .filter(trigger -> trigger.length > 2 && NumberUtils.isCreatable(trigger[1])).map(trigger -> Long.parseLong(trigger[1])).collect(Collectors.toSet());

            // Remove all old triggers
            for (final var triggerId : oldTriggers) {
                removeTrigger(planKey, triggerId, false);
            }

            // Add new triggers
            for (final var repo : triggeredBy) {
                addTrigger(planKey, repo);
            }
        }
        catch (CliClient.ClientException | CliClient.RemoteRestException e) {
            throw new BambooException("Unable to overwrite triggers for " + planKey + "\n" + e.getMessage(), e);
        }
    }

    public String getTriggerList(String plan, int limit, Pattern pattern) throws CliClient.ClientException, CliClient.RemoteRestException {
        boolean doPlanTrigger = true;
        RemotePlan remotePlan = getRemotePlan(plan);
        RemoteEnvironment remoteEnvironment = null;
        List<RemoteTrigger> list = this.getRemoteTriggerList(remotePlan, remoteEnvironment, limit, pattern, true);
        StringBuilder builder = new StringBuilder();
        if (!bambooClient.isExistingFileWithAppend()) {
            builder.append(RemoteTrigger.getCsvHeader(doPlanTrigger));
            builder.append('\n');
        }

        Iterator var12 = list.iterator();

        while (var12.hasNext()) {
            RemoteTrigger entry = (RemoteTrigger) var12.next();
            entry.appendCsv(builder);
            builder.append('\n');
        }

        String message = list.size() + " triggers in list";
        return bambooClient.standardFinishNew(message, list.size() > 0, builder);
    }

    public String removeTrigger(String plan, Long id, boolean doContinue) throws CliClient.ClientException, CliClient.RemoteRestException {
        RemotePlan remotePlan = getRemotePlan(plan);
        return this.removeTriggerInternal(remotePlan, id, doContinue);
    }

    protected String getNotFoundMessage(String name) {
        return "Deployment project " + CliUtils.quoteString(name) + " not found.";
    }

    public String removeTriggerInternal(RemotePlan remotePlan, Long id, boolean doContinue) throws CliClient.ClientException, CliClient.RemoteRestException {

        Map<String, String> parameters = new HashMap<>();
        parameters.put("triggerId", Long.toString(id));
        parameters.put("confirm", "true");
        parameters.put("decorator", "nothing");
        parameters.put("bamboo.successReturnMode", "json");
        String request;
        String messageFragment;
        request = "/chain/admin/config/deleteChainTrigger.action";
        parameters.put("planKey", remotePlan.getKey());
        messageFragment = "plan " + remotePlan.getKey();

        DefaultRequestHelper helper = bambooClient.getPseudoRequestHelper();
        helper.setRequestType(DefaultRequestHelper.RequestType.POST);
        helper.setContentType(DefaultRequestHelper.RequestContentType.JSON);
        helper.setParameters(parameters);
        helper.makeRequest(request);
        String responseData = helper.getResponseData();
        String regex = "(?s)<div\\s+[^>]*class=\"aui-message error\"[^>]*>\\s*<p>([^<]*)</p>";
        Matcher matcher = Pattern.compile(regex).matcher(responseData);
        String message;
        if (matcher.find()) {
            boolean throwException = false;
            String error = matcher.group(1);
            if (error.contains("no trigger with id")) {
                message = "Invalid trigger id.";
                if (doContinue) {
                    message = message + " Ignore.";
                }
                else {
                    throwException = true;
                }
            }
            else {
                message = error;
                throwException = true;
            }

            if (throwException) {
                throw new CliClient.ClientException(message);
            }
        }
        else {
            message = "Trigger " + id + " removed from " + messageFragment + ".";
        }

        return message;
    }

    protected List<RemoteTrigger> getRemoteTriggerList(RemotePlan remotePlan, RemoteEnvironment remoteEnvironment, int limit, Pattern pattern, boolean withDetails)
            throws CliClient.ClientException, CliClient.RemoteRestException {
        List<RemoteTrigger> list = new ArrayList();
        Map<String, String> parameters = new HashMap();
        String request;
        if (remotePlan == null) {
            request = "/deploy/config/configureEnvironmentTriggers.action";
            parameters.put("environmentId", remoteEnvironment.getIdString());
        }
        else {
            request = "/chain/admin/config/editChainTriggers.action";
            parameters.put("buildKey", remotePlan.getKey());
        }

        DefaultRequestHelper helper = bambooClient.getPseudoRequestHelper();
        helper.setParameters(parameters);
        helper.makeRequest(request);
        Document document = Jsoup.parse(helper.getResponseData());
        Iterator var11 = document.getElementsByClass("item").iterator();

        while (var11.hasNext()) {
            Element element = (Element) var11.next();
            RemoteTrigger entry = new RemoteTrigger(remotePlan, remoteEnvironment, CliUtils.getLong(element.attr("data-item-id"), 0L),
                    JsoupUtils.getText(element.getElementsByClass("item-title").first()), JsoupUtils.getText(element.getElementsByClass("item-description").first()));
            if ("Disabled".equalsIgnoreCase(JsoupUtils.getText(element.getElementsByClass("lozenge").first()))) {
                entry.setEnabled(false);
            }

            if (pattern == null || pattern.matcher(entry.getName()).matches() || pattern.matcher(entry.getDescription()).matches()) {
                if (withDetails) {
                    this.updateTriggerWithDetails(entry);
                }

                list.add(entry);
            }

            if (list.size() >= limit) {
                break;
            }
        }

        return list;
    }

    private void addTrigger(String plan, String repository) throws CliClient.ClientException, CliClient.RemoteRestException {
        RemotePlan remotePlan = StringUtils.isBlank(plan) ? null : getRemotePlan(plan);
        List<RemotePlan> successfulPlanList = getRemotePlanListFromCsv(null);
        RemoteTrigger trigger = this.addTrigger(remotePlan, successfulPlanList, repository);
        String name = StringUtils.isBlank(trigger.getDescription()) ? trigger.getName() : trigger.getDescription();
        CliUtils.quoteString(name);
        trigger.getIdString();
    }

    protected List<RemotePlan> getRemotePlanListFromCsv(String plans) throws CliClient.ClientException, CliClient.RemoteRestException {
        List<RemotePlan> list = null;
        if (StringUtils.isNotBlank(plans)) {
            list = new ArrayList();
            Iterator var3 = CsvUtilities.csvDataAsListWithSingleQuote(plans).iterator();

            while (var3.hasNext()) {
                String entry = (String) var3.next();
                list.add(getRemotePlan(entry));
            }
        }

        return list;
    }

    private RemoteTrigger addTrigger(RemotePlan remotePlan, List<RemotePlan> successfulPlanList, String repository)
            throws CliClient.ClientException, CliClient.RemoteRestException {

        Map<String, String> parameters = bambooClient.convertFieldParameters(null);

        Map<String, Collection<String>> moreParameters = null;
        TriggerHelper.TYPE type = TriggerHelper.TYPE.REMOTEBITBUCKETSERVER;
        String triggerKey = null;

        triggerKey = TriggerHelper.TYPE.getKey(type, (String) null);
        if (StringUtils.isNotBlank(repository)) {
            moreParameters = this.getRepositoryListParameters(remotePlan.getKey(), repository);
        }

        if (successfulPlanList != null && StringUtils.isBlank((CharSequence) parameters.get("custom.triggerrCondition.plansGreen.plans"))) {
            List<String> keyList = new ArrayList<>();
            Iterator var25 = successfulPlanList.iterator();

            while (var25.hasNext()) {
                RemotePlan entry = (RemotePlan) var25.next();
                keyList.add(entry.getKey());
            }

            parameters.put("custom.triggerrCondition.plansGreen.plans", CliUtils.listToSeparatedString(keyList, ","));
        }

        return this.addTriggerPart2(BambooClient.CONFIG_TYPE.PLAN, remotePlan, null, null, null, triggerKey, false, parameters, moreParameters);
    }

    private final Map<String, Collection<String>> getRepositoryListParameters(String plan, String repository) throws CliClient.RemoteRestException, CliClient.ClientException {
        Map<String, Collection<String>> moreParameters = new HashMap();
        if (StringUtils.isNotBlank(repository)) {
            Collection<String> idList = new ArrayList();
            if (!"@all".equalsIgnoreCase(repository)) {
                List<RemoteRepository> repositoryList = getRemoteRepositoryList(plan);
                List<String> nameList = CsvUtilities.csvDataAsListWithSingleQuote(repository);
                Iterator var7 = nameList.iterator();

                while (var7.hasNext()) {
                    String entry = (String) var7.next();
                    RemoteRepository remoteRepository = lookupRepository(entry, repositoryList);
                    if (remoteRepository == null) {
                        throw new CliClient.RemoteRestException("Repository not found with name " + CliUtils.quoteString(repository) + ".");
                    }

                    idList.add(getRemoteRepository(entry, plan).getIdString());
                }
            }
            else {
                Iterator var5 = this.getRemoteRepositoryList(plan).iterator();

                while (var5.hasNext()) {
                    RemoteRepository entry = (RemoteRepository) var5.next();
                    idList.add(entry.getIdString());
                }

                if (idList.isEmpty()) {
                    throw new CliClient.ClientException("Plan must have at least one associated repository.");
                }
            }

            moreParameters.put("repositoryTrigger", idList);
        }

        return moreParameters;
    }

    public RemoteTrigger addTriggerPart2(BambooClient.CONFIG_TYPE configType, RemotePlan remotePlan, RemoteDeploymentProject remoteDeploymentProject,
            RemoteEnvironment remoteEnvironment, String description, String triggerKey, boolean disable, Map<String, String> parameters,
            Map<String, Collection<String>> moreParameters) throws CliClient.ClientException, CliClient.RemoteRestException {
        if (parameters == null) {
            parameters = bambooClient.convertFieldParameters((String) null);
        }

        if (configType == BambooClient.CONFIG_TYPE.PLAN) {
            parameters.put("planKey", remotePlan.getKey());
        }
        else if (configType == BambooClient.CONFIG_TYPE.DEPLOY) {
            parameters.put("environmentId", remoteEnvironment.getIdString());
        }

        parameters.put("triggerId", "-1");
        parameters.put("createTriggerKey", triggerKey);
        parameters.put("userDescription", description);
        parameters.put("confirm", "true");
        parameters.put("bamboo.successReturnMode", "json");
        parameters.put("decorator", "nothing");
        if (disable) {
            parameters.put("triggerDisabled", "true");
        }

        if (StringUtils.isNotBlank((CharSequence) parameters.get("custom.triggerrCondition.plansGreen.plans"))
                && !parameters.containsKey("custom.triggerrCondition.plansGreen.enabled")) {
            parameters.put("custom.triggerrCondition.plansGreen.enabled", "true");
        }

        String parameterString = CliUtils.generateParameterString(parameters, false);
        if (moreParameters != null) {
            parameterString = parameterString + CliUtils.generateParameterStringFromCollection(moreParameters);
        }

        String request = "";
        if (configType == BambooClient.CONFIG_TYPE.PLAN) {
            request = "/chain/admin/config/createChainTrigger.action";
        }
        else if (configType == BambooClient.CONFIG_TYPE.DEPLOY) {
            request = "/deploy/config/createEnvironmentTrigger.action";
        }

        PseudoRequestHelper helper = bambooClient.getPseudoRequestHelper();

        try {
            helper.setRequestType(DefaultRequestHelper.RequestType.POST);
            helper.setContentType(DefaultRequestHelper.RequestContentType.JSON);
            helper.setParameters(parameterString);
            helper.makeRequest(request);
        }
        catch (CliClient.RemoteInternalServerErrorException var17) {
            throw new CliClient.ParameterClientException(
                    "Missing or invalid parameters for a custom trigger. Most likely cause is an invalid trigger key specified in the type parameter. Specify -v on the action and look in the detailed information for problem determination information.");
        }

        String responseData = helper.getResponseData();
        if (!JsonUtils.isJson(responseData)) {
            String regex = "<div\\s+[^>]*class=\"error control-form-error\"\\s+[^>]*data-field-name=\"([^\"]*)\"[^>]*>([^<]*)<";
            Matcher matcher = Pattern.compile(regex).matcher(responseData);
            String error;
            if (matcher.find()) {
                error = CliUtils.endWithPeriod(matcher.group(2)) + " The error was related to the field: " + CliUtils.endWithPeriod(matcher.group(1));
            }
            else {
                error = "Unknown error occurred.";
                if (bambooClient.getDebug()) {
                    bambooClient.getOut().println("response data: " + responseData);
                }

                if (responseData.contains("repository.change.poll.type=CRON")) {
                    error = error + " Cron expresssion may be invalid.";
                }
            }

            throw new CliClient.ClientException(error);
        }
        else {
            RemoteTrigger remoteTrigger = new RemoteTrigger(remotePlan, remoteEnvironment, helper.getResponseJsonLegacy());
            bambooClient.getReplaceMap().put("triggerId", remoteTrigger.getIdString());
            return remoteTrigger;
        }
    }

    public void updateTriggerWithDetails(RemoteTrigger trigger) throws CliClient.ClientException, CliClient.RemoteRestException {
        Map<String, String> parameters = new HashMap();
        parameters.put("triggerId", trigger.getIdString());
        parameters.put("confirm", "true");
        parameters.put("decorator", "nothing");
        parameters.put("bamboo.successReturnMode", "json");
        String request;
        if (trigger.isPlanTrigger()) {
            request = "/chain/admin/config/editChainTrigger.action";
            parameters.put("planKey", trigger.getPlan().getKey());
        }
        else {
            request = "/deploy/config/editEnvironmentTrigger.action";
            parameters.put("environmentId", trigger.getEnvironment().getIdString());
        }

        DefaultRequestHelper helper = bambooClient.getPseudoRequestHelper();
        helper.setRequestType(DefaultRequestHelper.RequestType.POST);
        helper.setContentType(DefaultRequestHelper.RequestContentType.JSON);
        helper.setParameters(parameters);
        helper.makeRequest(request);
        Document document = Jsoup.parse(helper.getResponseData());
        List<Element> list = document.getElementsByTag("input");
        list.addAll(document.getElementsByTag("select"));
        Iterator var7 = list.iterator();

        while (var7.hasNext()) {
            Element element = (Element) var7.next();
            if (bambooClient.getDebug()) {
                bambooClient.getOut().println(element.tagName() + ": " + element);
            }

            String fieldName = element.attr("name");
            if (StringUtils.isNotBlank(fieldName)) {
                fieldName = fieldName.trim();
                String value = element.attr("value");
                if (value == null) {
                    value = element.text();
                    if (value == null) {
                        value = "";
                    }
                }

                value = value.trim();
                byte var12 = -1;
                switch (fieldName.hashCode()) {
                    case -1984203622:
                        if (fieldName.equals("deployment.trigger.afterSuccessfulPlan.triggeringBranch")) {
                            var12 = 20;
                        }
                        break;
                    case -1792642865:
                        if (fieldName.equals("repository.change.poll.pollingPeriod")) {
                            var12 = 3;
                        }
                        break;
                    case -1440652555:
                        if (fieldName.equals("deployment.trigger.schedule.branchSelectionMode")) {
                            var12 = 9;
                        }
                        break;
                    case -1152422724:
                        if (fieldName.equals("deployment.trigger.schedule.sourcePlan")) {
                            var12 = 8;
                        }
                        break;
                    case -1045085253:
                        if (fieldName.equals("repository.change.poll.cronExpression")) {
                            var12 = 4;
                        }
                        break;
                    case -950205810:
                        if (fieldName.equals("environmentId")) {
                            var12 = 14;
                        }
                        break;
                    case -218871054:
                        if (fieldName.equals("deployment.trigger.afterSuccessfulDeployment.triggeringEnvironmentId")) {
                            var12 = 16;
                        }
                        break;
                    case -164537788:
                        if (fieldName.equals("deployment.trigger.schedule.triggeringBranch")) {
                            var12 = 18;
                        }
                        break;
                    case -28303137:
                        if (fieldName.equals("deployment.trigger.afterSuccessfulPlan.branchSelectionMode")) {
                            var12 = 12;
                        }
                        break;
                    case 202832300:
                        if (fieldName.equals("repository.change.daily.buildTime")) {
                            var12 = 6;
                        }
                        break;
                    case 494906126:
                        if (fieldName.equals("repositoryTrigger")) {
                            var12 = 15;
                        }
                        break;
                    case 562027235:
                        if (fieldName.equals("repository.change.schedule.cronExpression")) {
                            var12 = 1;
                        }
                        break;
                    case 674340454:
                        if (fieldName.equals("deployment.trigger.afterSuccessfulStage.branchSelectionMode")) {
                            var12 = 10;
                        }
                        break;
                    case 786495293:
                        if (fieldName.equals("deployment.trigger.afterSuccessfulDeployment.deploymentProjectId")) {
                            var12 = 13;
                        }
                        break;
                    case 1062419571:
                        if (fieldName.equals("deployment.trigger.afterSuccessfulStage.triggeringBranch")) {
                            var12 = 19;
                        }
                        break;
                    case 1106891871:
                        if (fieldName.equals("custom.triggerrCondition.plansGreen.plans")) {
                            var12 = 0;
                        }
                        break;
                    case 1435504141:
                        if (fieldName.equals("deployment.trigger.afterSuccessfulStage.triggeringStage")) {
                            var12 = 17;
                        }
                        break;
                    case 1584936993:
                        if (fieldName.equals("deployment.trigger.afterSuccessfulPlan.triggeringPlan")) {
                            var12 = 11;
                        }
                        break;
                    case 1652158777:
                        if (fieldName.equals("deployment.trigger.schedule.deploymentsMode")) {
                            var12 = 7;
                        }
                        break;
                    case 1865204037:
                        if (fieldName.equals("repository.change.trigger.triggerIpAddress")) {
                            var12 = 5;
                        }
                        break;
                    case 2082636143:
                        if (fieldName.equals("repository.change.poll.type")) {
                            var12 = 2;
                        }
                }

                switch (var12) {
                    case 0:
                    case 1:
                    case 2:
                    case 3:
                    case 4:
                    case 5:
                    case 6:
                    case 7:
                    case 8:
                    case 9:
                    case 10:
                    case 11:
                    case 12:
                    case 13:
                    case 14:
                        trigger.getFields().put(fieldName, value);
                        break;
                    case 15:
                        Element label = element.parent().getElementsByTag("label").first();
                        if (label != null) {
                            if (trigger.getInternalFields().get("repositories") == null) {
                                trigger.getInternalFields().put("repositories", label.text());
                            }
                            else {
                                trigger.getInternalFields().put("repositories", (String) trigger.getInternalFields().get(fieldName) + "," + label.text());
                            }
                        }
                        break;
                    case 16:
                        Elements selected = element.select("select option[selected]");
                        if (selected.size() > 0) {
                            trigger.getInternalFields().put("triggeringEnvironment", ((Element) selected.get(0)).text());
                        }
                        break;
                    case 17:
                        trigger.getFields().put(fieldName, value);
                        Long id = CliUtils.getLong(value, 0L);
                        if (id != null) {
                            RemoteStage stage = bambooClient.getPlanHelper().getStage(trigger.getEnvironment().getDeploymentProject().getPlanKey(), id, false);
                            if (stage != null) {
                                trigger.getInternalFields().put("stage", stage.getName());
                                trigger.getInternalFields().put("stageId", stage.getIdString());
                            }
                        }
                        break;
                    case 18:
                    case 19:
                    case 20:
                        trigger.getFields().put(fieldName, value);
                        if (!trigger.getEnvironment().getDeploymentProject().getPlanKey().equals(value)) {
                            RemoteBranch branch = bambooClient.getBranchHelper().getBranchByKey(trigger.getEnvironment().getDeploymentProject().getPlanKey(), value, false);
                            if (branch != null) {
                                trigger.getInternalFields().put("branch", branch.getShortName());
                                trigger.getInternalFields().put("branchKey", branch.getFullKey());
                            }
                        }
                }
            }
        }

    }
}
