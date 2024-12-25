package ${packageName}

type Policy struct {
	context *Context
}

const datesSizeThreshold = 10

func NewPolicy(context *Context) *Policy {
	return &Policy{context}
}

// Configure chooses a strategy depending on the number of date objects.
func (p *Policy) Configure() {
	if len(p.context.GetDates()) > datesSizeThreshold {
		p.context.SetSortAlgorithm(NewMergeSort())
	} else {
		p.context.SetSortAlgorithm(NewBubbleSort())
	}
}
