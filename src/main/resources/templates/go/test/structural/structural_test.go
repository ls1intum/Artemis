package test

import (
	"testing"
	"time"

	"artemis/${packageName}"
)

type SortStrategy interface {
	PerformSort(input []time.Time)
}

type Context interface {
	GetDates() []time.Time
	SetDates(dates []time.Time)
	GetSortAlgorithm() ${packageName}.SortStrategy
	SetSortAlgorithm(strategy ${packageName}.SortStrategy)
}

type Policy interface {
	Configure()
}

func TestBubbleSort(t *testing.T) {
	defer handlePanic(t)

	var bubbleSort interface{} = new(${packageName}.BubbleSort)

	_ = bubbleSort.(SortStrategy)
}

func TestMergeSort(t *testing.T) {
	defer handlePanic(t)

	var mergeSort interface{} = new(${packageName}.MergeSort)

	_ = mergeSort.(SortStrategy)
}

func TestContext(t *testing.T) {
	defer handlePanic(t)

	var context interface{} = new(${packageName}.Context)

	_ = context.(Context)
}

func TestPolicy(t *testing.T) {
	defer handlePanic(t)

	var policy interface{} = new(${packageName}.Policy)

	_ = policy.(Policy)
}

// handlePanic fatally fails the test without terminating the test suite.
func handlePanic(t *testing.T) {
	if err := recover(); err != nil {
		t.Fatal("panic:", err)
	}
}
