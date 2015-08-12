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
package org.springframework.cloud.data.rest.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.metrics.Metric;
import org.springframework.boot.actuate.metrics.repository.MetricRepository;
import org.springframework.cloud.data.core.Counter;
import org.springframework.cloud.data.rest.resource.CounterResource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.ExposesResourceFor;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.mvc.ResourceAssemblerSupport;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for counters.
 *
 * @author Ilayaperumal Gopinathan
 */
@RestController
@RequestMapping("/metrics/counters")
@ExposesResourceFor(CounterResource.class)
//todo: Create base class Metric controller
public class CounterController {

	@Autowired
	MetricRepository metricRepository;

	/**
	 * Assembler for {@link CounterResource} objects.
	 */
	private final Assembler counterAssembler = new Assembler();


	@RequestMapping("/{name:.+}")
	@ResponseStatus(HttpStatus.OK)
	public CounterResource getMetricValue(@PathVariable String name) {
		//todo: handle NoSuchMetricException
		Metric<?> counter = metricRepository.findOne("counter." + name);
		return counterAssembler.toResource(new Counter(counter.getName(), Math.round((Double) counter.getValue())));
	}

	@RequestMapping
	@ResponseStatus(HttpStatus.OK)
	public PagedResources<CounterResource> list(Pageable pageable,
			PagedResourcesAssembler<Counter> assembler,
			@RequestParam(value = "detailed", defaultValue = "false") boolean detailed) {
		List<Counter> metrics = new ArrayList<>();
		for (Metric<?> metric: metricRepository.findAll()) {
			metrics.add(new Counter(metric.getName(), Math.round((Double)metric.getValue())));
		}
		return assembler.toResource(new PageImpl<>(metrics, pageable, metrics.size()), counterAssembler);
	}

	/**
	 * {@link org.springframework.hateoas.ResourceAssembler} implementation
	 * that converts {@link Counter}s to {@link CounterResource}s.
	 */
	class Assembler extends ResourceAssemblerSupport<Counter, CounterResource> {

		public Assembler() {
			super(CounterController.class, CounterResource.class);
		}

		@Override
		public CounterResource toResource(Counter counter) {
			String name = counter.getName();
			return createResourceWithId(name.substring(name.indexOf(".") + 1), counter);
		}

		@Override
		public CounterResource instantiateResource(Counter counter) {
			String name = counter.getName();
			return new CounterResource(name.substring(name.indexOf(".") + 1),  Math.round(counter.getValue()));
		}
	}
}
