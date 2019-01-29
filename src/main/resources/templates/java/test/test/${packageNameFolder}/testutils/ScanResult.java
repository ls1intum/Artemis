package ${packageName}.testutils;

/**
 * @author Kristian Dimo (kristian.dimo@tum.de)
 * @version 1.1 (28.01.2018)
 *
 * This class represents the result object generated from the ClassNamesScanner.
 * It consists of an enum representing the type of the result and a string 
 * representing the feedback tied to that result type.
 */
public class ScanResult {

	private ScanResultType type;
	private String message;
	
	public ScanResult(ScanResultType result, String message) {
		this.type = result;
		this.message = message;
	}
	
	public String getMessage() {
		return message;
	}

	public ScanResultType getResult() {
		return type;
	}
}
