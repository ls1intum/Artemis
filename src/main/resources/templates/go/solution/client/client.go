package main

import (
	"fmt"
	"math/rand"
	"time"

	implementation "artemis/${packageName}"
)

// Constants define iteration and random date generation bounds.
const (
	Iterations    = 10
	RandomFloor   = 5
	RandomCeiling = 15
)

// main demonstrates the sorting process.
func main() {
	// Init Context and Policy
	context := implementation.NewContext()
	policy := implementation.NewPolicy(context)

	// Run multiple times to simulate different sorting strategies
	for i := 0; i < Iterations; i++ {
		dates := createRandomDates()

		context.SetDates(dates)
		policy.Configure()

		fmt.Println("Unsorted Array of course dates:")
		printDates(dates)
		fmt.Println()

		context.Sort()

		fmt.Println("Sorted Array of course dates:")
		printDates(dates)
		fmt.Println()
	}
}

// createRandomDates generates a slice of random dates with size between RandomFloor and RandomCeiling.
func createRandomDates() []time.Time {
	listLength := rand.Intn(RandomCeiling-RandomFloor) + RandomFloor
	list := make([]time.Time, listLength)

	lowestDate := time.Date(2024, 10, 15, 0, 0, 0, 0, time.UTC)
	highestDate := time.Date(2025, 1, 15, 0, 0, 0, 0, time.UTC)

	for i := 0; i < listLength; i++ {
		list[i] = randomDateWithin(lowestDate, highestDate)
	}
	return list
}

// randomDateWithin creates a random time.Time value within the given range.
func randomDateWithin(low, high time.Time) time.Time {
	randomTime := rand.Int63n(high.Unix()-low.Unix()) + low.Unix()
	return time.Unix(randomTime, 0)
}

// printDates prints out the given slice of time.Time values as dates.
func printDates(dates []time.Time) {
	for _, date := range dates {
		fmt.Println(date.Format(time.DateOnly))
	}
}
