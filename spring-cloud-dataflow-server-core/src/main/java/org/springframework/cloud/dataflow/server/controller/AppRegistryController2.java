/*
 * Copyright 2015-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.dataflow.server.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.configurationmetadata.ConfigurationMetadataProperty;
import org.springframework.cloud.dataflow.configuration.metadata.ApplicationConfigurationMetadataResolver;
import org.springframework.cloud.dataflow.core.ApplicationType;
import org.springframework.cloud.dataflow.registry.skipper.AppRegistration2;
import org.springframework.cloud.dataflow.registry.skipper.AppRegistry2;
import org.springframework.cloud.dataflow.registry.support.NoSuchAppRegistrationException;
import org.springframework.cloud.dataflow.rest.resource.AppRegistrationResource;
import org.springframework.cloud.dataflow.rest.resource.AppRegistrationResource2;
import org.springframework.cloud.dataflow.rest.resource.DetailedAppRegistrationResource;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.ExposesResourceFor;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.mvc.ResourceAssemblerSupport;
import org.springframework.http.HttpStatus;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * Handles all {@link AppRegistry2} related interactions.
 *
 * @author Glenn Renfro
 * @author Mark Fisher
 * @author Gunnar Hillert
 * @author Eric Bottard
 * @author Gary Russell
 * @author Patrick Peralta
 * @author Thomas Risberg
 */
@RestController
@RequestMapping("/apps")
@ExposesResourceFor(AppRegistrationResource.class)
public class AppRegistryController2 implements ResourceLoaderAware {

	private static final Logger logger = LoggerFactory.getLogger(AppRegistryController2.class);

	private final Assembler assembler = new Assembler();

	private final AppRegistry2 appRegistry;

	private ApplicationConfigurationMetadataResolver metadataResolver;

	private ResourceLoader resourceLoader = new DefaultResourceLoader();

	private ForkJoinPool forkJoinPool;

	public AppRegistryController2(AppRegistry2 appRegistry, ApplicationConfigurationMetadataResolver metadataResolver,
			ForkJoinPool forkJoinPool) {
		this.appRegistry = appRegistry;
		this.metadataResolver = metadataResolver;
		this.forkJoinPool = forkJoinPool;
	}

	/**
	 * List app registrations. Optional type and search parameters can be passed to do
	 * filtering. Search parameter only filters by {@code AppRegistration} name field.
	 *
	 * @param pagedResourcesAssembler the resource assembler for app registrations
	 * @param type the application type: source, sink, processor, task
	 * @param search optional search parameter
	 * @return the list of registered applications
	 */
	@RequestMapping(method = RequestMethod.GET)
	@ResponseStatus(HttpStatus.OK)
	public PagedResources<? extends AppRegistrationResource2> list(
			Pageable pageable,
			PagedResourcesAssembler<AppRegistration2> pagedResourcesAssembler,
			@RequestParam(value = "type", required = false) ApplicationType type,
			@RequestParam(required = false) String search) {
		Page<AppRegistration2> pagedRegistrations;
		if (type == null && search == null) {
			pagedRegistrations = appRegistry.findAll(pageable);
		}
		else {
			List<AppRegistration2> appRegistrations = appRegistry.findAll().stream()
					.filter(ar -> (type != null ? ar.getType() == type : true))
					.filter(ar -> (StringUtils.hasText(search) ? ar.getName().contains(search) : true))
					.collect(Collectors.toList());
			long count = appRegistrations.size();
			long to = Math.min(count, pageable.getOffset() + pageable.getPageSize());

			// if a request for page is higher than number of items we actually have is either
			// a rogue request or user was in high pages and applied filtering.
			// in this case we simply reset to first page.
			// we also need to explicitly set page and see what offset is when
			// building new page.
			// all this is done because we don't use a proper repository which would
			// handle all these automatically.
			int offset = 0;
			int page = 0;
			if (pageable.getOffset() <= to) {
				offset = pageable.getOffset();
				page = pageable.getPageNumber();
			}
			else if (pageable.getOffset() + pageable.getPageSize() <= to) {
				offset = pageable.getOffset();
			}
			pagedRegistrations = new PageImpl<>(appRegistrations.subList(offset, (int) to),
					new PageRequest(page, pageable.getPageSize()), appRegistrations.size());
		}

		return pagedResourcesAssembler.toResource(pagedRegistrations, this.assembler);

	}

	/**
	 * Retrieve detailed information about a particular application.
	 *
	 * @param type application type
	 * @param name application name
	 * @param version application version
	 * @return detailed application information
	 */
	@RequestMapping(value = "/{type}/{name}/{version}", method = RequestMethod.GET)
	@ResponseStatus(HttpStatus.OK)
	public DetailedAppRegistrationResource info(@PathVariable("type") ApplicationType type,
			@PathVariable("name") String name, @PathVariable("version") String version) {
		AppRegistration2 registration = appRegistry.find(name, type, version);
		if (registration == null) {
			throw new NoSuchAppRegistrationException(name, type); // TODO add version
		}
		DetailedAppRegistrationResource result = new DetailedAppRegistrationResource(
				assembler.toResource(registration));
		List<ConfigurationMetadataProperty> properties = metadataResolver
				.listProperties(appRegistry.getAppMetadataResource(registration));
		for (ConfigurationMetadataProperty property : properties) {
			result.addOption(property);
		}
		return result;
	}

	@Deprecated
	@RequestMapping(value = "/{type}/{name}", method = RequestMethod.GET)
	@ResponseStatus(HttpStatus.OK)
	public DetailedAppRegistrationResource info(@PathVariable("type") ApplicationType type,
			@PathVariable("name") String name) {
		String defaultVersion = appRegistry.getDefaultApp(name, type).getVersion();
		return info(type, name, defaultVersion);
	}

	/**
	 * Register a module name and type with its URI.
	 *
	 * @param type module type
	 * @param name module name
	 * @param version module version
	 * @param uri URI for the module artifact (e.g. {@literal maven://group:artifact:version})
	 * @param metadataUri URI for the metadata artifact
	 * @param force if {@code true}, overwrites a pre-existing registration
	 */
	@RequestMapping(value = "/{type}/{name}/{version}", method = RequestMethod.POST)
	@ResponseStatus(HttpStatus.CREATED)
	public void register(@PathVariable("type") ApplicationType type, @PathVariable("name") String name,
			@PathVariable("version") String version,
			@RequestParam("uri") String uri, @RequestParam(name = "metadata-uri", required = false) String metadataUri,
			@RequestParam(value = "force", defaultValue = "false") boolean force) {
		AppRegistration2 previous = appRegistry.find(name, type, version);
		if (!force && previous != null) {
			throw new AppAlreadyRegisteredException2(previous);
		}
		try {
			AppRegistration2 registration = appRegistry.save(name, type, version, new URI(uri),
					metadataUri != null ? new URI(metadataUri) : null);
			prefetchMetadata(Arrays.asList(registration));
		}
		catch (URISyntaxException e) {
			throw new IllegalArgumentException(e);
		}
	}

	@Deprecated
	@RequestMapping(value = "/{type}/{name}", method = RequestMethod.POST)
	@ResponseStatus(HttpStatus.CREATED)
	public void register(@PathVariable("type") ApplicationType type, @PathVariable("name") String name,
			@RequestParam("uri") String uri, @RequestParam(name = "metadata-uri", required = false) String metadataUri,
			@RequestParam(value = "force", defaultValue = "false") boolean force) {
		String defaultVersion = appRegistry.getDefaultApp(name, type).getVersion();
		this.register(type, name, defaultVersion, uri, metadataUri, force);
	}

	/**
	 * Set a module version as default
	 *
	 * @param type module type
	 * @param name module name
	 * @param version module version
	 */
	@RequestMapping(value = "/{type}/{name}/{version}", method = RequestMethod.PUT)
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void makeDefault(@PathVariable("type") ApplicationType type, @PathVariable("name") String name,
			@PathVariable("version") String version) {
		appRegistry.setDefaultApp(name, type, version);
	}

	/**
	 * Unregister an application by name and type. If the application does not exist, a
	 * {@link NoSuchAppRegistrationException} will be thrown.
	 *
	 * @param type the application type
	 * @param name the application name
	 * @param version application version
	 */
	@RequestMapping(value = "/{type}/{name}/{version}", method = RequestMethod.DELETE)
	@ResponseStatus(HttpStatus.OK)
	public void unregister(@PathVariable("type") ApplicationType type, @PathVariable("name") String name,
			@PathVariable("version") String version) {
		appRegistry.delete(name, type, version);
	}

	@Deprecated
	@RequestMapping(value = "/{type}/{name}", method = RequestMethod.DELETE)
	@ResponseStatus(HttpStatus.OK)
	public void unregister(@PathVariable("type") ApplicationType type, @PathVariable("name") String name) {
		String defaultVersion = appRegistry.getDefaultApp(name, type).getVersion();
		this.unregister(type, name, defaultVersion);
	}

	/**
	 * Register all applications listed in a properties file or provided as key/value pairs.
	 *
	 * @param pagedResourcesAssembler the resource asembly for app registrations
	 * @param uri URI for the properties file
	 * @param apps key/value pairs representing applications, separated by newlines
	 * @param force if {@code true}, overwrites any pre-existing registrations
	 * @return the collection of registered applications
	 * @throws IOException if can't store the Properties object to byte output stream
	 */
	@RequestMapping(method = RequestMethod.POST)
	@ResponseStatus(HttpStatus.CREATED)
	public PagedResources<? extends AppRegistrationResource2> registerAll(
			Pageable pageable,
			PagedResourcesAssembler<AppRegistration2> pagedResourcesAssembler,
			@RequestParam(value = "uri", required = false) String uri,
			@RequestParam(value = "apps", required = false) Properties apps,
			@RequestParam(value = "force", defaultValue = "false") boolean force) throws IOException {
		List<AppRegistration2> registrations = new ArrayList<>();
		if (StringUtils.hasText(uri)) {
			registrations.addAll(appRegistry.importAll(force, resourceLoader.getResource(uri)));
		}
		else if (!CollectionUtils.isEmpty(apps)) {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			apps.store(baos, "");
			ByteArrayResource bar = new ByteArrayResource(baos.toByteArray(), "Inline properties");
			registrations.addAll(appRegistry.importAll(force, bar));
		}
		Collections.sort(registrations);
		prefetchMetadata(registrations);
		return pagedResourcesAssembler.toResource(
				new PageImpl<>(registrations, pageable, appRegistry.findAll().size()),
				assembler);
	}

	/**
	 * Trigger early resolution of the metadata resource of registrations that have an
	 * explicit metadata artifact. This assumes usage of
	 * {@link org.springframework.cloud.deployer.resource.support.DelegatingResourceLoader}.
	 */
	private void prefetchMetadata(List<AppRegistration2> appRegistrations) {
		forkJoinPool.execute(() -> {
			appRegistrations.stream().filter(r -> r.getMetadataUri() != null).parallel().forEach(r -> {
				logger.info("Eagerly fetching {}", r.getMetadataUri());
				try {
					appRegistry.getAppMetadataResource(r);
				}
				catch (Exception e) {
					logger.warn("Could not fetch " + r.getMetadataUri(), e);
				}
			});
		});
	}

	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	class Assembler extends ResourceAssemblerSupport<AppRegistration2, AppRegistrationResource2> {

		public Assembler() {
			super(AppRegistryController2.class, AppRegistrationResource2.class);
		}

		@Override
		public AppRegistrationResource2 toResource(AppRegistration2 registration) {
			return createResourceWithId(String.format("%s/%s/%s", registration.getType(), registration.getName(),
					registration.getVersion()), registration);
		}

		@Override
		protected AppRegistrationResource2 instantiateResource(AppRegistration2 registration) {
			return new AppRegistrationResource2(registration.getName(), registration.getType().name(),
					registration.getVersion(), registration.isDefault(),
					registration.getUri().toString());
		}
	}
}
