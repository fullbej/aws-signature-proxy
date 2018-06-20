package com.coveo.awsproxy.token;

import com.amazonaws.http.HttpResponse;
import com.amazonaws.http.HttpResponseHandler;
import com.coveo.awsproxy.domain.exception.AWSAccessDeniedException;
import com.coveo.awsproxy.domain.exception.AWSBadRequestException;
import com.coveo.awsproxy.domain.exception.AWSMovedPermanentlyException;
import com.coveo.awsproxy.domain.exception.AWSNotAuthenticatedException;
import com.coveo.awsproxy.domain.exception.AWSResponseException;

public class AWSErrorResponseHandler implements HttpResponseHandler<AWSResponseException>
{

    @Override
    public AWSResponseException handle(HttpResponse response) throws Exception
    {
        int statusCode = response.getStatusCode();
        if (statusCode >= 300 && statusCode < 400) {
            switch (statusCode) {
                case 301:
                    return new AWSMovedPermanentlyException(response);
                default:
                    return new AWSResponseException("3XX service exception", response);
            }
        }
        if (statusCode >= 400 && statusCode < 500) {
            switch (statusCode) {
                case 401:
                    return new AWSNotAuthenticatedException(response);
                case 403:
                    return new AWSAccessDeniedException(response);
                default:
                    return new AWSBadRequestException(response);
            }
        }
        return new AWSResponseException("service exception", response);
    }

    @Override
    public boolean needsConnectionLeftOpen()
    {
        return false;
    }

}
