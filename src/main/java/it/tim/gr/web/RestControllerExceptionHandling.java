package it.tim.gr.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import it.tim.gr.model.exception.ErrorResponseException;
import it.tim.gr.model.web.ErrorResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by alongo on 16/04/18.
 */
@ControllerAdvice
@Slf4j
public class RestControllerExceptionHandling {

    @ExceptionHandler(value = ErrorResponseException.class)
    protected ResponseEntity<Object> handleErrorResponseException(ErrorResponseException ex, WebRequest request){
        if(ex.isToLog())
            log.error("An error occured: " + ex);
        return new ResponseEntity<>(
                new ErrorResponse(ex.getErrorCode(), ex.getOutputMessage()),
                ex.getHttpStatus()
        );
    }

}
