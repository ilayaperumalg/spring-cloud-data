/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.data.rest.resource;

import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.ResourceSupport;

/**
 * Base class for resources that implements boot metric.
 *
 * @author Ilayaperumal Gopinathan
 */
public abstract class MetricResource extends ResourceSupport {

	/**
	 * Name of the metric.
	 */
	private String name;

	/**
	 * The value for the metric.
	 */
	private long value;

	/**
	 * Construct a new resource.
	 */
	//todo: support timestamp?
	public MetricResource(String name, long value) {
		this.name = name;
		this.value = value;
	}

	/**
	 * No arg constructor for serialization frameworks.
	 */
	protected MetricResource() {

	}

	public static class Page extends PagedResources<MetricResource> {

	}

}
