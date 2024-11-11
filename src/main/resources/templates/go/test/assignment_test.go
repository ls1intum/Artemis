package test

import (
	"testing"

	"artemis/assignment"
)

func TestAdd(t *testing.T) {
	expected := 5
	actual := assignment.Add(2, 3)
	if actual != expected {
		t.Fatalf("expected: %d, got %d", expected, actual)
	}
}
