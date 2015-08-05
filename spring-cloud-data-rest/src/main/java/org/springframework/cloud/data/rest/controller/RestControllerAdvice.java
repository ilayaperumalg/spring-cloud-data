package org.springframework.cloud.data.rest.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.hateoas.VndErrors;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Apply common behavior (exception handling etc.,) to all the REST controllers.
 *
 * @author Eric Bottard
 * @author Gunnar Hillert
 * @author Ilayaperumal Gopinathan
 */
@ControllerAdvice
public class RestControllerAdvice {

	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * Handles the general error case. Report server-side error.
	 */
	@ExceptionHandler(Exception.class)
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	@ResponseBody
	public VndErrors onException(Exception e) {
		String logref = logError(e);
		String msg = StringUtils.hasText(e.getMessage()) ? e.getMessage() : e.getClass().getSimpleName();
		return new VndErrors(logref, msg);
	}

	private String logError(Throwable t) {
		logger.error("Caught exception while handling a request", t);
		return t.getClass().getSimpleName();
	}
}
