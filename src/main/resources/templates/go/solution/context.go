package ${packageName}

import "time"

type Context struct {
	dates         []time.Time
	sortAlgorithm SortStrategy
}

func NewContext() *Context {
	return new(Context)
}

func (c *Context) GetDates() []time.Time {
	return c.dates
}

func (c *Context) SetDates(dates []time.Time) {
	c.dates = dates
}

func (c *Context) GetSortAlgorithm() SortStrategy {
	return c.sortAlgorithm
}

func (c *Context) SetSortAlgorithm(strategy SortStrategy) {
	c.sortAlgorithm = strategy
}

// Sort runs the configured sort algorithm.
func (c *Context) Sort() {
	if c.sortAlgorithm == nil {
		panic("sortAlgorithm has not been set")
	}

	if c.dates == nil {
		panic("dates has not been set")
	}

	c.sortAlgorithm.PerformSort(c.dates)
}
