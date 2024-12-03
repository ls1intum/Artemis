package test

import (
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

func TestBubbleSort(t *testing.T) {
	defer handlePanic(t)

	var bubbleSort interface{} = new(assignment.BubbleSort)

	_ = bubbleSort.(SortStrategy)
}

func TestMergeSort(t *testing.T) {
	defer handlePanic(t)

	var mergeSort interface{} = new(assignment.MergeSort)

	_ = mergeSort.(SortStrategy)
}

func TestContext(t *testing.T) {
	defer handlePanic(t)

	var context interface{} = new(assignment.Context)

	_ = context.(Context)
}

func TestPolicy(t *testing.T) {
	defer handlePanic(t)

	var policy interface{} = new(assignment.Policy)

	_ = policy.(Policy)
}

// handlePanic fatally fails the test without terminating the test suite.
func handlePanic(t *testing.T) {
	if err := recover(); err != nil {
		t.Fatal("panic:", err)
	}
}
