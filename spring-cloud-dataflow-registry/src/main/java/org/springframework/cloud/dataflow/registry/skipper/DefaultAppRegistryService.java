/*
 * Copyright 2017 the original author or authors.
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

package org.springframework.cloud.dataflow.registry.skipper;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.dataflow.core.ApplicationType;
import org.springframework.cloud.dataflow.registry.AppRegistration;
import org.springframework.cloud.dataflow.registry.support.NoSuchAppRegistrationException2;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Convenience wrapper for the {@link } that operates on higher level
 * {@link DefaultAppRegistryService} objects and supports on-demand loading of {@link Resource}s.
 * <p>
 * <p>
 * Stores AppRegistration with up to two keys:
 * </p>
 * <ul>
 * <li>{@literal <type>.<name>}: URI for the actual app</li>
 * <li>{@literal <type>.<name>.metadata}: Optional URI for the app metadata</li>
 * </ul>
 *
 * @author Mark Fisher
 * @author Gunnar Hillert
 * @author Thomas Risberg
 * @author Eric Bottard
 * @author Ilayaperumal Gopinathan
 * @author Oleg Zhurakousky
 * @author Christian Tzolov
 */
@Transactional
public class DefaultAppRegistryService implements AppRegistryService {

	private static final Logger logger = LoggerFactory.getLogger(DefaultAppRegistryService.class);

	private static final String METADATA_KEY_SUFFIX = "metadata";

	private final AppRegistrationRepository appRegistrationRepository;

	private final ResourceLoader resourceLoader;

	public DefaultAppRegistryService(AppRegistrationRepository appRegistrationRepository, ResourceLoader resourceLoader) {
		Assert.notNull(appRegistrationRepository, "'appRegistrationRepository' must not be null");
		Assert.notNull(resourceLoader, "'resourceLoader' must not be null");
		this.appRegistrationRepository = appRegistrationRepository;
		this.resourceLoader = resourceLoader;
	}

	public ResourceLoader getResourceLoader() {
		return this.resourceLoader;
	}

	public Resource getAppMetadataResource(AppRegistration appRegistration) {
		return appRegistration.getMetadataUri() != null ? this.resourceLoader.getResource(
				appRegistration.getMetadataUri().toString()) : null;
	}

	public Resource getAppResource(AppRegistration appRegistration) {
		return this.resourceLoader.getResource(appRegistration.getUri().toString());
	}

	@Override
	public AppRegistration find(String name, ApplicationType type) {
		return this.getDefaultApp(name, type);
	}

	@Override
	public AppRegistration find(String name, ApplicationType type, String version) {
		return this.appRegistrationRepository.findAppRegistrationByNameAndTypeAndVersion(name, type, version);
	}

	@Override
	public AppRegistration getDefaultApp(String name, ApplicationType type) {
		return this.appRegistrationRepository.findAppRegistrationByNameAndTypeAndDefaultVersionIsTrue(name, type);
	}

	@Override
	public void setDefaultApp(String name, ApplicationType type, String version) {
		AppRegistration newDefault = this.appRegistrationRepository
				.findAppRegistrationByNameAndTypeAndVersion(name,
						type, version);

		if (newDefault == null) {
			throw new NoSuchAppRegistrationException2(name, type, version);
		}

		newDefault.setDefaultVersion(true);

		AppRegistration oldDefault = this.appRegistrationRepository
				.findAppRegistrationByNameAndTypeAndDefaultVersionIsTrue(name, type);
		if (oldDefault != null) {
			oldDefault.setDefaultVersion(false);
			this.appRegistrationRepository.save(oldDefault);
		}
		this.appRegistrationRepository.save(newDefault);
	}

	@Override
	public List<AppRegistration> findAll() {
		return this.appRegistrationRepository.findAll();
	}

	@Override
	public Page<AppRegistration> findAll(Pageable pageable) {
		List<AppRegistration> appRegistrations = this.findAll();
		long to = Math.min(appRegistrations.size(), pageable.getOffset() + pageable.getPageSize());

		// if a request for page is higher than number of items we actually have is either
		// a rogue request.
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

		return new PageImpl<>(appRegistrations.subList(offset, (int) to), new PageRequest(page, pageable.getPageSize()),
				appRegistrations.size());
	}

	public AppRegistration save(String name, ApplicationType type, String version, URI uri, URI metadataUri) {
		return this.appRegistrationRepository.save(new AppRegistration(name, type, version, uri, metadataUri));
	}

	/**
	 * Deletes an {@link AppRegistration}. If the {@link AppRegistration} does not exist, a
	 * {@link NoSuchAppRegistrationException2} will be thrown.
	 *
	 * @param name Name of the AppRegistration to delete
	 * @param type Type of the AppRegistration to delete
	 * @param version Version of the AppRegistration to delete
	 */
	public void delete(String name, ApplicationType type, String version) {
		this.appRegistrationRepository.deleteAppRegistrationByNameAndTypeAndVersion(name, type, version);
		// TODO select new default
	}

	public List<AppRegistration> importAll(boolean overwrite, Resource... resources) {
		return Stream.of(resources)
				.map(this::loadProperties)
				.flatMap(prop -> prop.entrySet().stream()
						.map(toStringAndUriFUNC)
						.flatMap(kv -> toValidAppRegistration(kv, metadataUriFromProperties(kv.getKey(), prop)))
						.filter(a -> isOverwrite(a, overwrite))
						.map(ar -> save(ar.getName(), ar.getType(), ar.getVersion(), ar.getUri(), ar.getMetadataUri())))
				.collect(Collectors.toList());
	}

	private boolean isOverwrite(AppRegistration app, boolean overwrite) {
		return overwrite || this.appRegistrationRepository.findAppRegistrationByNameAndTypeAndVersion(app.getName(),
				app.getType(), app.getVersion()) == null;
	}

	private Properties loadProperties(Resource resource) {
		try {
			return PropertiesLoaderUtils.loadProperties(resource);
		}
		catch (IOException e) {
			throw new RuntimeException("Error reading from " + resource.getDescription(), e);
		}
	}

	private static final Function<Entry<Object, Object>, AbstractMap.SimpleImmutableEntry<String, URI>> toStringAndUriFUNC = kv -> {
		try {
			return new AbstractMap.SimpleImmutableEntry<>((String) kv.getKey(), new URI((String) kv.getValue()));
		}
		catch (URISyntaxException e) {
			throw new IllegalArgumentException(e);
		}
	};

	/**
	 * Builds a {@link Stream} from key/value mapping.
	 * @return
	 * <ul>
	 * <li>valid AppRegistration as single element Stream</li>
	 * <li>silently ignores well malformed metadata entries (0 element Stream) or</li>
	 * <li>fails otherwise.</li>
	 * </ul>
	 *
	 * @param kv key/value representing app key (key) and app URI (value)
	 * @param metadataURI metadataUri computed from a given app key
	 */
	private Stream<AppRegistration> toValidAppRegistration(Entry<String, URI> kv, URI metadataURI) {
		String key = kv.getKey();
		String[] tokens = key.split("\\.");
		if (tokens.length == 2) {
			String name = tokens[1];
			ApplicationType type = ApplicationType.valueOf(tokens[0]);
			URI appURI = warnOnMalformedURI(key, kv.getValue());

			Resource appResource = resourceLoader.getResource(appURI.toString());
			// TODO use org.springframework.cloud.dataflow.server.support.ResourceUtils to extract the
			// version form URI
			String version = appURI.getSchemeSpecificPart()
					.substring(appURI.getSchemeSpecificPart().lastIndexOf(":") + 1);

			return Stream.of(new AppRegistration(name, type, version, appURI, metadataURI));
		}
		else {
			Assert.isTrue(tokens.length == 3 && METADATA_KEY_SUFFIX.equals(tokens[2]),
					"Invalid format for app key '" + key + "'in file. Must be <type>.<name> or <type>.<name>"
							+ ".metadata");
			return Stream.empty();
		}
	}

	private URI metadataUriFromProperties(String key, Properties properties) {
		String metadataValue = properties.getProperty(key + "." + METADATA_KEY_SUFFIX);
		try {
			return metadataValue != null ? warnOnMalformedURI(key, new URI(metadataValue)) : null;
		}
		catch (URISyntaxException e) {
			throw new IllegalArgumentException(e);
		}
	}

	private URI warnOnMalformedURI(String key, URI uri) {
		if (StringUtils.isEmpty(uri)) {
			logger.warn(String.format("Error when registering '%s': URI is required", key));
		}
		else if (!StringUtils.hasText(uri.getScheme())) {
			logger.warn(
					String.format("Error when registering '%s' with URI %s: URI scheme must be specified", key, uri));
		}
		else if (!StringUtils.hasText(uri.getSchemeSpecificPart())) {
			logger.warn(String.format("Error when registering '%s' with URI %s: URI scheme-specific part must be " +
					"specified", key, uri));
		}
		return uri;
	}

	@Override
	public boolean appExist(String name, ApplicationType type) {
		return getDefaultApp(name, type) != null;
	}

	@Override
	public boolean appExist(String name, ApplicationType type, String version) {
		return find(name, type, version) != null;
	}
}
