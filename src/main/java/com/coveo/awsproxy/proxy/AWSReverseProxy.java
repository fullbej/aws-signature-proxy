/**
 * Copyright (c) 2011 - 2017, Coveo Solutions Inc.
 */
package com.coveo.awsproxy.proxy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
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
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.auth.StaticSignerProvider;
import com.amazonaws.http.AmazonHttpClient;
import com.amazonaws.http.ExecutionContext;
import com.amazonaws.http.HttpMethodName;
import com.amazonaws.http.HttpResponse;
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
    @Value("${aws.mfaserial:}")
    private String mfaSerial;
    @Value("${aws.rolearn}")
    private String roleArn;
    @Value("${aws.servicename}")
    private String serviceName;
    @Value("${aws.region}")
    private String region;
    @Value("${aws.mfacode:}")
    private String mfaCode;

    @Autowired
    private AWSSRoleSessionCredentialsRetriever AWSSRoleSessionCredentialsRetriever;
    private BasicSessionCredentials sessionCredentials;
    private AmazonHttpClient amazonHttpClient = new AmazonHttpClient(new ClientConfiguration());

    @RequestMapping("/**")
    public void reverseProxy(HttpServletRequest request, HttpServletResponse response)
            throws IOException,
                URISyntaxException
    {
        logger.info("Proxying request: {}", request.getRequestURI());
        Request<Void> signedRequest = buildSignedRequest(request.getMethod(),
                                                         new URI(serviceEndpoint + request.getRequestURI()),
                                                         request,
                                                         serviceName);
        AWS4Signer signer = new AWS4Signer(true);
        signer.setRegionName(region);
        signer.setServiceName(serviceName);

        try {
            ExecutionContext executionContext = ExecutionContext.builder()
                                                                .withSignerProvider(new StaticSignerProvider(signer))
                                                                .build();
            executionContext.setCredentialsProvider(new AWSStaticCredentialsProvider(sessionCredentials));
            Response<AmazonWebServiceResponse<byte[]>> awsResponse = amazonHttpClient.requestExecutionBuilder()
                                                                                     .errorResponseHandler(new AWSErrorResponseHandler())
                                                                                     .executionContext(executionContext)
                                                                                     .request(signedRequest)
                                                                                     .execute(new AWSResponseHandler());
            fillResponse(response, awsResponse.getHttpResponse(), awsResponse.getAwsResponse().getResult());
        } catch (AWSResponseException ex) {
            // AWS http client will throw an exception for anything not 200.
            // We have to set the http response on the exception to retrieve it.
            //String errorResponse = ex.getResponseContent() == null ? "" : new String(ex.getResponseContent());
            //logger.info("Got a response <> 200. code {}. response: {}",
            //             ex.getHttpResponse().getStatusCode(),
            //            errorResponse);
            fillResponse(response, ex.getHttpResponse(), ex.getResponseContent());
        }
    }

    private void fillResponse(HttpServletResponse servletResponse, HttpResponse awsHttpResponse, byte[] body)
            throws IOException
    {
        // Set status code first
        servletResponse.setStatus(awsHttpResponse.getStatusCode());

        // We pass along any header received by AWS to the caller.
        if (awsHttpResponse.getHeaders() != null) {
            for (Map.Entry<String, String> entry : awsHttpResponse.getHeaders().entrySet()) {
                servletResponse.setHeader(entry.getKey(), entry.getValue());
            }
        }

        // We pass along any body received by AWS to the caller.
        // We have to do this step last because the response will be commited
        // and we won't be able to change headers or code (sent first to remote)
        if (body != null && body.length > 0) {
            servletResponse.getOutputStream().write(body);
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
                                                                                                          "awssignatureproxy",
                                                                                                          mfaSerial,
                                                                                                          mfaCode,
                                                                                                          accessKey,
                                                                                                          secretKey,
                                                                                                          7200);
            } else {
                // NO MFA
                sessionCredentials = AWSSRoleSessionCredentialsRetriever.getRoleSessionCredentials(roleArn,
                                                                                                   region,
                                                                                                   "awssignatureproxy",
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

    private Request<Void> buildSignedRequest(String verb,
                                             URI fullTargetUri,
                                             HttpServletRequest servletRequest,
                                             String service)
            throws URISyntaxException,
                IOException
    {
        Request<Void> awsRequest = new DefaultRequest<>(service);
        awsRequest.setEndpoint(new URI(fullTargetUri.getScheme(), fullTargetUri.getAuthority(), null, null));
        //awsRequest.setEndpoint(new URI(fullTargetUri + "?" + servletRequest.getQueryString()));

        // We process query params to make sure we pass them along to AWS.
        if (servletRequest.getQueryString() != null) {
            List<NameValuePair> params = URLEncodedUtils.parse(servletRequest.getQueryString(), StandardCharsets.UTF_8);
            for (NameValuePair param : params) {
                awsRequest.withParameter(param.getName(), param.getValue());
            }
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

        Map<String, String> headers = new HashMap<>();

        // Here we take all the headers sent by the caller and add them to the request.
        // Note that referer, content-length and host are discarted.
        if (servletRequest.getHeaderNames() != null) {
            Enumeration<String> headerNames = servletRequest.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                headers.put(headerName, servletRequest.getHeader(headerName));
            }
        }
        List<String> blacklistedHeaders = Arrays.asList("accept",
                                                        "host",
                                                        "referer",
                                                        "content-length",
                                                        "user-agent",
                                                        "origin");
        headers = headers.entrySet()
                         .stream()
                         .filter(entry -> !blacklistedHeaders.contains(entry.getKey()))
                         .collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue()));
        awsRequest.getHeaders().putAll(headers);

        // Set the body if any
        if (servletRequest.getInputStream() != null) {
            byte[] dataArray = IOUtils.toByteArray(servletRequest.getInputStream());
            awsRequest.setContent(new ByteArrayInputStream(dataArray));
            if (dataArray.length == 0) {
                awsRequest.addHeader("Content-Type", "application/json");
            }
        }

        return awsRequest;
    }
}
