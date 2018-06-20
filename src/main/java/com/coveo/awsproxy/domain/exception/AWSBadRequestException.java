package com.coveo.awsproxy.domain.exception;

import com.amazonaws.http.HttpResponse;

public class AWSBadRequestException extends AWSResponseException
{
    private static final long serialVersionUID = 2809110887844475984L;

    public AWSBadRequestException(HttpResponse httpResponse)
    {
        super("Bad Request", httpResponse);
    }
}
