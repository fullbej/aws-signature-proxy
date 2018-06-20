package com.coveo.awsproxy.domain.exception;

import java.io.IOException;

import org.apache.commons.io.IOUtils;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.http.HttpResponse;

public class AWSResponseException extends AmazonServiceException
{
    private static final long serialVersionUID = -7354337160990252750L;
    private byte[] responseContent;
    private HttpResponse httpResponse;

    public AWSResponseException(String errorMessage, HttpResponse httpResponse)
    {
        super(errorMessage);
        setResponseProperties(httpResponse);
    }

    public byte[] getResponseContent()
    {
        return responseContent;
    }

    public HttpResponse getHttpResponse()
    {
        return httpResponse;
    }

    public String getDisplayableString()
    {
        return String.format("Request: %s %s Headers: %s.\n Response: %s Headers: %s",
                             httpResponse.getRequest().getHttpMethod(),
                             httpResponse.getRequest().getResourcePath(),
                             httpResponse.getRequest().getHeaders(),
                             httpResponse.getStatusCode(),
                             httpResponse.getHeaders());
    }

    private void setResponseProperties(HttpResponse httpResponse)
    {
        this.httpResponse = httpResponse;
        if (httpResponse.getContent() != null) {
            try {
                responseContent = IOUtils.toByteArray(httpResponse.getContent());
            } catch (IOException ex) {
            }
        }

        setStatusCode(httpResponse.getStatusCode());
    }
}
