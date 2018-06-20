package com.coveo.awsproxy.domain.exception;

import com.amazonaws.http.HttpResponse;

public class AWSAccessDeniedException extends AWSResponseException
{
    private static final long serialVersionUID = 6338717806710710060L;

    public AWSAccessDeniedException(HttpResponse httpResponse)
    {
        super("Access denied", httpResponse);
    }

}
