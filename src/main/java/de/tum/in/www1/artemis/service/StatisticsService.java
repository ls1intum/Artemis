package de.tum.in.www1.artemis.service;

import java.time.*;
import java.util.*;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.enumeration.GraphType;
import de.tum.in.www1.artemis.domain.enumeration.SpanType;
import de.tum.in.www1.artemis.repository.StatisticsRepository;

@Service
public class StatisticsService {

    private final StatisticsRepository statisticsRepository;

    public StatisticsService(StatisticsRepository statisticsRepository) {
        this.statisticsRepository = statisticsRepository;
    }

    /**
     * Forwards the request to the repository, which returns a List<Map<String, Object>>. For week, month or year the map from the Repository contains a String with the column name,
     * "day" and "amount" and an Object being the value, either the date in the format "YYYY-MM-DD" or the amount of the findings. For day, the column names are "day" and "amount",
     * which then contains the date in the ZonedDateFormat as Integer and the amount as Long
     * It then collects the amounts in an array, depending on the span value, and returns it
     *
     * @param span DAY,WEEK,MONTH or YEAR depending on the active tab in the view
     * @param periodIndex an index indicating which time period, 0 is current week, -1 is one week in the past, -2 is two weeks in the past ...
     * @param graphType the type of graph the data should be fetched
     * @return an array, containing the values for each bar in the graph
     */
    public Integer[] getChartData(SpanType span, Integer periodIndex, GraphType graphType) {
        ZonedDateTime startDate;
        ZonedDateTime endDate;
        List<Map<String, Object>> outcome;
        ZonedDateTime now = ZonedDateTime.now();
        int lengthOfMonth = YearMonth.of(now.getYear(), now.minusMonths(1 - periodIndex).plusDays(1).getMonth()).lengthOfMonth();
        Integer[] result = new Integer[createSpanMap(lengthOfMonth).get(span)];
        Arrays.fill(result, 0);
        switch (span) {
            case DAY:
                startDate = now.minusDays(-periodIndex).withHour(0).withMinute(0).withSecond(0);
                endDate = now.minusDays(-periodIndex).withHour(23).withMinute(59).withSecond(59);
                outcome = getDataFromDatabaseForDay(startDate, endDate, graphType);
                return createResultArrayForDay(outcome, result, endDate);
            case WEEK:
                startDate = now.minusWeeks(-periodIndex).minusDays(6).withHour(0).withMinute(0).withSecond(0);
                endDate = now.minusWeeks(-periodIndex).withHour(23).withMinute(59).withSecond(59);
                outcome = getDataFromDatabase(startDate, endDate, graphType);
                return createResultArrayForWeek(outcome, result, endDate);
            case MONTH:
                startDate = now.minusMonths(1 - periodIndex).plusDays(1).withHour(0).withMinute(0).withSecond(0);
                endDate = now.minusMonths(-periodIndex).withHour(23).withMinute(59).withSecond(59);
                outcome = getDataFromDatabase(startDate, endDate, graphType);
                return createResultArrayForMonth(outcome, result, endDate);
            case YEAR:
                startDate = now.minusYears(1).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
                lengthOfMonth = YearMonth.of(now.minusYears(-periodIndex).getYear(), now.minusYears(-periodIndex).getMonth()).lengthOfMonth();
                endDate = now.minusYears(-periodIndex).withDayOfMonth(lengthOfMonth).withHour(23).withMinute(59).withSecond(59);
                outcome = getDataFromDatabase(startDate, endDate, graphType);
                return createResultArrayForYear(outcome, result, endDate);
            default:
                return null;
        }
    }

    /**
     * A map to manage the spanTypes and the corresponding array length of the result
     */
    private Map<SpanType, Integer> createSpanMap(Integer lengthOfMonth) {
        Map<SpanType, Integer> spanMap = new HashMap<>();
        spanMap.put(SpanType.DAY, 24);
        spanMap.put(SpanType.WEEK, 7);
        spanMap.put(SpanType.MONTH, lengthOfMonth);
        spanMap.put(SpanType.YEAR, 12);
        return spanMap;
    }

    /**
     * Gets a List of Maps, each Map describing an entry in the database. The Map has the two keys "day" and "amount",
     * which map to the date and the amount of the findings. This method handles the spanType DAY
     *
     * @param outcome A List<Map<String, Object>>, containing the content which should be refactored into an array
     * @param result the array in which the converted outcome should be inserted
     * @param endDate the endDate
     * @return an array, containing the values for each bar in the graph
     */
    private Integer[] createResultArrayForDay(List<Map<String, Object>> outcome, Integer[] result, ZonedDateTime endDate) {
        for (Map<String, Object> map : outcome) {
            int hour = ((ZonedDateTime) map.get("day")).getHour();
            Integer amount = map.get("amount") != null ? ((Long) map.get("amount")).intValue() : null;
            for (int i = 0; i < 24; i++) {
                if (hour == endDate.minusHours(i).getHour()) {
                    result[endDate.getHour() - i] += amount;
                }
            }
        }
        return result;
    }

    /**
     * Gets a List of Maps, each Map describing an entry in the database. The Map has the two keys "day" and "amount",
     * which map to the date and the amount of the findings. This method handles the spanType WEEK
     *
     * @param outcome A List<Map<String, Object>>, containing the content which should be refactored into an array
     * @param result the array in which the converted outcome should be inserted
     * @param endDate the endDate
     * @return an array, containing the values for each bar in the graph
     */
    private Integer[] createResultArrayForWeek(List<Map<String, Object>> outcome, Integer[] result, ZonedDateTime endDate) {
        for (Map<String, Object> map : outcome) {
            LocalDate localDate = LocalDate.parse(map.get("day").toString());
            ZonedDateTime date = localDate.atStartOfDay(ZoneId.systemDefault());
            Integer amount = map.get("amount") != null ? ((Long) map.get("amount")).intValue() : null;
            for (int i = 0; i < 7; i++) {
                if (date.getDayOfMonth() == endDate.minusDays(i).getDayOfMonth()) {
                    result[6 - i] += amount;
                }
            }
        }
        return result;
    }

    /**
     * Gets a List of Maps, each Map describing an entry in the database. The Map has the two keys "day" and "amount",
     * which map to the date and the amount of the findings. This method handles the spanType MONTH
     *
     * @param outcome A List<Map<String, Object>>, containing the content which should be refactored into an array
     * @param result the array in which the converted outcome should be inserted
     * @param endDate the endDate
     * @return an array, containing the values for each bar in the graph
     */
    private Integer[] createResultArrayForMonth(List<Map<String, Object>> outcome, Integer[] result, ZonedDateTime endDate) {
        for (Map<String, Object> map : outcome) {
            LocalDate localDate = LocalDate.parse(map.get("day").toString());
            ZonedDateTime date = localDate.atStartOfDay(ZoneId.systemDefault());
            Integer amount = map.get("amount") != null ? ((Long) map.get("amount")).intValue() : null;
            for (int i = 0; i < result.length; i++) {
                if (date.getDayOfMonth() == endDate.minusDays(i).getDayOfMonth()) {
                    result[result.length - 1 - i] += amount;
                }
            }
        }
        return result;
    }

    /**
     * Gets a List of Maps, each Map describing an entry in the database. The Map has the two keys "day" and "amount",
     * which map to the date and the amount of the findings. This method handles the spanType YEAR
     *
     * @param outcome A List<Map<String, Object>>, containing the content which should be refactored into an array
     * @param result the array in which the converted outcome should be inserted
     * @param endDate the endDate
     * @return an array, containing the values for each bar in the graph
     */
    private Integer[] createResultArrayForYear(List<Map<String, Object>> outcome, Integer[] result, ZonedDateTime endDate) {
        for (Map<String, Object> map : outcome) {
            LocalDate localDate = LocalDate.parse(map.get("day").toString());
            ZonedDateTime date = localDate.atStartOfDay(ZoneId.systemDefault());
            Integer amount = map.get("amount") != null ? ((Long) map.get("amount")).intValue() : null;
            for (int i = 0; i < 12; i++) {
                if (date.getMonth() == endDate.minusMonths(i).getMonth() && date.getYear() == endDate.minusMonths(i).getYear()) {
                    result[11 - i] += amount;
                }
            }
        }
        return result;
    }

    /**
     * Handles the Repository calls depending on the graphType for the span "day"
     *
     * @param startDate The startDate of which the data should be fetched
     * @param endDate The endDate of which the data should be fetched
     * @param graphType the type of graph the data should be fetched for (see GraphType.java)
     * @return the return value of the database call
     */
    private List<Map<String, Object>> getDataFromDatabaseForDay(ZonedDateTime startDate, ZonedDateTime endDate, GraphType graphType) {
        switch (graphType) {
            case SUBMISSIONS -> {
                return this.statisticsRepository.getTotalSubmissionsDay(startDate, endDate);
            }
            case ACTIVE_USERS -> {
                List<Map<String, Object>> returnList = new ArrayList<>();
                Map<Integer, List<String>> users = new HashMap<>();
                List<Map<String, Object>> result = this.statisticsRepository.getActiveUsersDay(startDate, endDate);
                for (int i = 0; i < result.size(); i++) {
                    Map<String, Object> listElement = result.get(i);
                    ZonedDateTime date = (ZonedDateTime) listElement.get("day");
                    String username = listElement.get("amount").toString();
                    List<String> usersInSameHour = users.get(date.getHour());
                    // if this hour is not yet registered
                    if (usersInSameHour == null) {
                        usersInSameHour = new ArrayList<>();
                        usersInSameHour.add(username);
                        users.put(date.getHour(), usersInSameHour);
                        // if this hour does not contain this username
                    }
                    else if (!usersInSameHour.contains("" + listElement.get("amount"))) {
                        usersInSameHour.add(username);
                        users.put(date.getHour(), usersInSameHour);
                    }
                }
                users.forEach((k, v) -> {
                    Map<String, Object> listElement = new HashMap<>();
                    listElement.put("day", startDate.withHour(k));
                    listElement.put("amount", (long) v.size());
                    returnList.add(listElement);
                });
                return returnList;
            }
            case RELEASED_EXERCISES -> {
                return this.statisticsRepository.getReleasedExercisesDay(startDate, endDate);
            }
        }
        return new ArrayList<>();
    }

    /**
     * Handles the Repository calls depending on the graphType for the span "week", "month" and "year"
     *
     * @param startDate The startDate of which the data should be fetched
     * @param endDate The endDate of which the data should be fetched
     * @param graphType the type of graph the data should be fetched for (see GraphType.java)
     * @return the return value of the database call
     */
    private List<Map<String, Object>> getDataFromDatabase(ZonedDateTime startDate, ZonedDateTime endDate, GraphType graphType) {
        return switch (graphType) {
            case SUBMISSIONS -> this.statisticsRepository.getTotalSubmissions(startDate, endDate);
            case ACTIVE_USERS -> this.statisticsRepository.getActiveUsers(startDate, endDate);
            case RELEASED_EXERCISES -> this.statisticsRepository.getReleasedExercises(startDate, endDate);
        };
    }
}
