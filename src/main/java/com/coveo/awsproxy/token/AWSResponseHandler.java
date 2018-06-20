package com.coveo.awsproxy.token;

import java.io.IOException;

import com.amazonaws.AmazonWebServiceResponse;
import com.amazonaws.ResponseMetadata;
import com.amazonaws.http.HttpResponseHandler;
import com.amazonaws.util.IOUtils;

public class AWSResponseHandler implements HttpResponseHandler<AmazonWebServiceResponse<byte[]>>
{
    @Override
    public AmazonWebServiceResponse<byte[]> handle(com.amazonaws.http.HttpResponse response) throws IOException
    {

        AmazonWebServiceResponse<byte[]> awsResponse = new AmazonWebServiceResponse<>();
        awsResponse.setResult(IOUtils.toByteArray(response.getContent()));
        awsResponse.setResponseMetadata(new ResponseMetadata(response.getHeaders()));

        return awsResponse;
    }

    @Override
    public boolean needsConnectionLeftOpen()
    {
        return false;
    }

}
