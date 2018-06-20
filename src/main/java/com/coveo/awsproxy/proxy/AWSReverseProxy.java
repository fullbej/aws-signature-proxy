/**
 * Copyright (c) 2011 - 2017, Coveo Solutions Inc.
 */
package com.coveo.awsproxy.proxy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import com.amazonaws.AmazonWebServiceResponse;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.DefaultRequest;
import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.auth.AWS4Signer;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.http.AmazonHttpClient;
import com.amazonaws.http.ExecutionContext;
import com.amazonaws.http.HttpMethodName;
import com.coveo.awsproxy.domain.exception.AWSResponseException;
import com.coveo.awsproxy.domain.exception.CouldNotGetSessionCredentialsException;
import com.coveo.awsproxy.token.AWSErrorResponseHandler;
import com.coveo.awsproxy.token.AWSResponseHandler;
import com.coveo.awsproxy.token.AWSSRoleSessionCredentialsRetriever;

@Controller
public class AWSReverseProxy
{
    private static final Logger logger = LoggerFactory.getLogger(AWSReverseProxy.class);

    @Value("${aws.endpoint}")
    private String serviceEndpoint;
    @Value("${aws.accesskey}")
    private String accessKey;
    @Value("${aws.secretaccesskey}")
    private String secretKey;
    @Value("${aws.mfaserial}")
    private String mfaSerial;
    @Value("${aws.rolearn}")
    private String roleArn;
    @Value("${aws.servicename}")
    private String serviceName;
    @Value("${aws.region}")
    private String region;
    @Value("${aws.mfacode}")
    private String mfaCode;

    @Autowired
    private AWSSRoleSessionCredentialsRetriever AWSSRoleSessionCredentialsRetriever;
    private BasicSessionCredentials sessionCredentials;

    @RequestMapping("/**")
    public void reverseProxy(HttpServletRequest request, HttpServletResponse response)
            throws IOException,
                URISyntaxException
    {
        logger.info("Proxying request: {}", request.getRequestURI());
        InputStreamEntity inputStreamEntity = getInputStreamEntity(request);

        logger.debug("Got input stream entity.");
        Request<Void> signedRequest = buildSignedRequest(request.getMethod(),
                                                         serviceEndpoint + request.getRequestURI(),
                                                         request,
                                                         inputStreamEntity,
                                                         serviceName);

        try {
            Response<AmazonWebServiceResponse<byte[]>> awsResponse = new AmazonHttpClient(new ClientConfiguration()).requestExecutionBuilder()
                                                                                                                    .errorResponseHandler(new AWSErrorResponseHandler())
                                                                                                                    .executionContext(new ExecutionContext(true))
                                                                                                                    .request(signedRequest)
                                                                                                                    .execute(new AWSResponseHandler());
            response.setStatus(awsResponse.getHttpResponse().getStatusCode());

            // We pass along any header received by AWS to the caller.
            if (awsResponse.getHttpResponse().getHeaders() != null) {
                for (Map.Entry<String, String> entry : awsResponse.getHttpResponse().getHeaders().entrySet()) {
                    response.setHeader(entry.getKey(), entry.getValue());
                }
            }

            // We pass along any body received by AWS to the caller.
            if (awsResponse.getAwsResponse().getResult() != null
                    && awsResponse.getAwsResponse().getResult().length > 0) {
                response.getOutputStream().write(awsResponse.getAwsResponse().getResult());
            }

        } catch (AWSResponseException ex) {
            // Some unexpected error occured. Forward headers and content to caller.
            logger.info("Got an error response. {}", ex.getDisplayableString());
            if (ex.getHttpResponse().getHeaders() != null) {
                for (Map.Entry<String, String> entry : ex.getHttpResponse().getHeaders().entrySet()) {
                    response.setHeader(entry.getKey(), entry.getValue());
                }
            }
            if (ex.getResponseContent() != null) {
                response.getOutputStream().write(ex.getResponseContent());
            }

            response.setStatus(ex.getHttpResponse().getStatusCode());
        }
    }

    @PostConstruct
    public void initialize() throws CouldNotGetSessionCredentialsException
    {
        logger.info("Initializing reverse proxy...");
        logger.info("rolearn: {}", roleArn);
        logger.info("region: {}", region);
        logger.info("mfaSerial: {}", mfaSerial);
        logger.info("mfaCode: {}", mfaCode);
        logger.info("accessKey: {}", accessKey);
        logger.info("secretKey: {}", "XXXX");
        try {
            if (mfaSerial != null && mfaSerial.length() > 0 && mfaCode != null && mfaCode.length() > 0) {
                // Using MFA
                sessionCredentials = AWSSRoleSessionCredentialsRetriever.getRoleSessionCredentialsWithMfa(roleArn,
                                                                                                          region,
                                                                                                          "kibanaReverse",
                                                                                                          mfaSerial,
                                                                                                          mfaCode,
                                                                                                          accessKey,
                                                                                                          secretKey,
                                                                                                          7200);
            } else {
                // NO MFA
                sessionCredentials = AWSSRoleSessionCredentialsRetriever.getRoleSessionCredentials(roleArn,
                                                                                                   region,
                                                                                                   "kibanaReverse",
                                                                                                   accessKey,
                                                                                                   secretKey,
                                                                                                   7200);
            }
        } catch (CouldNotGetSessionCredentialsException ex) {
            logger.error("Could not get session token", ex);
            throw ex;
        } catch (Exception ex) {
            logger.error("Unhandled exception while trying to initialize the reverse proxy.", ex);
            throw ex;
        }
    }

    private InputStreamEntity getInputStreamEntity(HttpServletRequest request) throws IOException
    {
        int contentLength = request.getContentLength();

        ContentType contentType = null;
        if (request.getContentType() != null) {
            contentType = ContentType.parse(request.getContentType());
        }

        return new InputStreamEntity(request.getInputStream(), contentLength, contentType);
    }

    private Request<Void> buildSignedRequest(String verb,
                                             String uri,
                                             HttpServletRequest servletRequest,
                                             InputStreamEntity inputStreamEntity,
                                             String service)
            throws URISyntaxException,
                IOException
    {
        URI fullTargetUri = new URI(uri);
        Request<Void> awsRequest = new DefaultRequest<>(service);
        awsRequest.setEndpoint(new URI(fullTargetUri.getScheme(), fullTargetUri.getAuthority(), null, null));

        // We process query params to make sure we pass them along to AWS.
        if (servletRequest.getQueryString() != null) {
            Map<String, List<String>> parameters = new HashMap<>();
            List<NameValuePair> params = URLEncodedUtils.parse(servletRequest.getQueryString(),
                                                               Charset.forName("UTF-8"));
            for (NameValuePair param : params) {
                parameters.put(param.getName(), Arrays.asList(param.getValue()));
            }
            awsRequest.setParameters(parameters);
        }

        // Set the request path properly.
        awsRequest.setResourcePath(fullTargetUri.getPath());

        switch (verb.toUpperCase()) {
            case "POST":
                awsRequest.setHttpMethod(HttpMethodName.POST);
                break;
            case "PUT":
                awsRequest.setHttpMethod(HttpMethodName.PUT);
                break;
            case "PATCH":
                awsRequest.setHttpMethod(HttpMethodName.PATCH);
                break;
            case "DELETE":
                awsRequest.setHttpMethod(HttpMethodName.DELETE);
                break;
            case "OPTIONS":
                awsRequest.setHttpMethod(HttpMethodName.OPTIONS);
                break;
            default:
                awsRequest.setHttpMethod(HttpMethodName.GET);
        }

        // Set the body if any
        if (inputStreamEntity.getContent() != null) {
            byte[] dataArray = IOUtils.toByteArray(inputStreamEntity.getContent());
            awsRequest.setContent(new ByteArrayInputStream(dataArray));
        }

        // We sign the request so AWS will accept it.
        AWS4Signer signer = new AWS4Signer(true);
        signer.setRegionName(region);
        signer.setServiceName(awsRequest.getServiceName());
        signer.sign(awsRequest, sessionCredentials);

        // Here we take all the headers sent by the caller and add them to the request.
        // Note that referer, content-length and host are discarted.
        if (servletRequest.getHeaderNames() != null) {
            Enumeration<String> headerNames = servletRequest.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                if (!headerName.toLowerCase().equals("referer") && !headerName.toLowerCase().equals("host")
                        && !headerName.toLowerCase().equals("content-length")) {
                    awsRequest.addHeader(headerName, servletRequest.getHeader(headerName));
                }
            }
        }

        return awsRequest;
    }
}
