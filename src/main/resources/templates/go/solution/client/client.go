package main

import (
	"artemis/assignment"
	"fmt"
)

func main() {
	var a, b int

	fmt.Print("a: ")
	fmt.Scanln(&a)

	fmt.Print("b: ")
	fmt.Scanln(&b)

	sum := assignment.Add(a, b)

	fmt.Printf("a + b = %d\n", sum)
}
