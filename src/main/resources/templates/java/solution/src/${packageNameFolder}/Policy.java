package ${packageName};

public class Policy {
	
	private Context context;

	public Policy(Context context) {
		this.context = context;
	}

	public void configure (){
		if(this.context.getDates().size() > 10) {
			System.out.println("More than 10 dates, choosing merge sort!");
			this.context.setSortAlgorithm(new MergeSort());
		} else {
			System.out.println("Less or equal than 10 dates. choosing quick sort!");
			this.context.setSortAlgorithm(new BubbleSort());
		}
	}
}
