package test

import (
	"slices"
	"testing"
	"time"

	assignment "artemis/${packageName}"
)

type SortStrategy interface {
	PerformSort(input []time.Time)
}

type Context interface {
	GetDates() []time.Time
	SetDates(dates []time.Time)
	GetSortAlgorithm() assignment.SortStrategy
	SetSortAlgorithm(strategy assignment.SortStrategy)
}

type Policy interface {
	Configure()
}

func TestBubbleSortSortsCorrectly(t *testing.T) {
	defer handlePanic(t)

	dates, datesWithCorrectOrder := createTestDates()

	var bubbleSortAny interface{} = assignment.NewBubbleSort()
	bubbleSort := bubbleSortAny.(SortStrategy)
	bubbleSort.PerformSort(dates)

	if !slices.Equal(dates, datesWithCorrectOrder) {
		t.Fatalf("expected: %v, got %v", datesWithCorrectOrder, dates)
	}
}

func TestMergeSortSortsCorrectly(t *testing.T) {
	defer handlePanic(t)

	dates, datesWithCorrectOrder := createTestDates()

	var mergeSortAny interface{} = assignment.NewMergeSort()
	mergeSort := mergeSortAny.(SortStrategy)
	mergeSort.PerformSort(dates)

	if !slices.Equal(dates, datesWithCorrectOrder) {
		t.Fatalf("expected: %v, got %v", datesWithCorrectOrder, dates)
	}
}

func createTestDates() ([]time.Time, []time.Time) {
	dates := []time.Time{
		time.Date(2018, 11, 8, 0, 0, 0, 0, time.UTC),
		time.Date(2017, 4, 15, 0, 0, 0, 0, time.UTC),
		time.Date(2016, 2, 15, 0, 0, 0, 0, time.UTC),
		time.Date(2017, 9, 15, 0, 0, 0, 0, time.UTC),
	}
	datesWithCorrectOrder := []time.Time{
		time.Date(2016, 2, 15, 0, 0, 0, 0, time.UTC),
		time.Date(2017, 4, 15, 0, 0, 0, 0, time.UTC),
		time.Date(2017, 9, 15, 0, 0, 0, 0, time.UTC),
		time.Date(2018, 11, 8, 0, 0, 0, 0, time.UTC),
	}

	return dates, datesWithCorrectOrder
}

func TestUseMergeSortForBigList(t *testing.T) {
	defer handlePanic(t)

	bigList := make([]time.Time, 0)
	for i := 0; i < 11; i++ {
		bigList = append(bigList, time.Unix(0, 0))
	}
	chosenSortStrategy := configurePolicyAndContext(bigList)
	_, ok := chosenSortStrategy.(*assignment.MergeSort)
	if !ok {
		t.Fatal("The sort algorithm of Context was not MergeSort for a list with more than 10 dates.")
	}
}

func TestUseBubbleSortForSmallList(t *testing.T) {
	defer handlePanic(t)

	bigList := make([]time.Time, 0)
	for i := 0; i < 3; i++ {
		bigList = append(bigList, time.Unix(0, 0))
	}
	chosenSortStrategy := configurePolicyAndContext(bigList)
	_, ok := chosenSortStrategy.(*assignment.BubbleSort)
	if !ok {
		t.Fatal("The sort algorithm of Context was not BubbleSort for a list with less than 10 dates.")
	}
}

func configurePolicyAndContext(dates []time.Time) interface{} {
	contextOriginal := assignment.NewContext()
	var contextAny interface{} = contextOriginal
	context := contextAny.(Context)
	context.SetDates(dates)

	var policyAny interface{} = assignment.NewPolicy(contextOriginal)
	policy := policyAny.(Policy)
	policy.Configure()

	chosenSortStrategy := context.GetSortAlgorithm()
	return chosenSortStrategy
}

// handlePanic fatally fails the test without terminating the test suite.
func handlePanic(t *testing.T) {
	if err := recover(); err != nil {
		t.Fatal("panic:", err)
	}
}
