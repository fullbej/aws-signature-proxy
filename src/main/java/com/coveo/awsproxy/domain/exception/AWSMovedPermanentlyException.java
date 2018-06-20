package com.coveo.awsproxy.domain.exception;

import com.amazonaws.http.HttpResponse;

public class AWSMovedPermanentlyException extends AWSResponseException
{
    private static final long serialVersionUID = -6012179004583276770L;

    public AWSMovedPermanentlyException(HttpResponse httpResponse)
    {
        super("Moved Permanently", httpResponse);
    }
}
