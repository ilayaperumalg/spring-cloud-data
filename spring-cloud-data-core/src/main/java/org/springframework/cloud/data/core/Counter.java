package org.springframework.cloud.data.core;

/**
 * @author Ilayaperumal Gopinathan
 */
public class Counter {

	private String name;

	private long value;

	public Counter(String name, long value) {
		this.name = name;
		this.value = value;
	}

	public String getName() {
		return name;
	}

	public long getValue() {
		return value;
	}

}
