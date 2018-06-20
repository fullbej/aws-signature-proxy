package com.coveo.awsproxy.domain.exception;

import com.amazonaws.http.HttpResponse;

public class AWSNotAuthenticatedException extends AWSResponseException
{
    private static final long serialVersionUID = -3051470279411269019L;

    public AWSNotAuthenticatedException(HttpResponse httpResponse)
    {
        super("Not Authenticated", httpResponse);
    }

}
