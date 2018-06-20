package com.coveo.awsproxy.token;

import org.springframework.stereotype.Component;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleResult;
import com.coveo.awsproxy.domain.exception.CouldNotGetSessionCredentialsException;

@Component
public class AWSSRoleSessionCredentialsRetriever
{
    public BasicSessionCredentials getRoleSessionCredentials(String roleArn,
                                                             String region,
                                                             String roleSessionName,
                                                             String credentialsProfile,
                                                             int duration)
            throws CouldNotGetSessionCredentialsException
    {
        return getRoleSessionCredentials(roleArn,
                                         region,
                                         roleSessionName,
                                         new ProfileCredentialsProvider(credentialsProfile),
                                         duration);
    }

    public BasicSessionCredentials getRoleSessionCredentials(String roleArn,
                                                             String region,
                                                             String roleSessionName,
                                                             String accesKey,
                                                             String accessSecret,
                                                             int duration)
            throws CouldNotGetSessionCredentialsException
    {
        return getRoleSessionCredentials(roleArn,
                                         region,
                                         roleSessionName,
                                         new AWSStaticCredentialsProvider(new BasicAWSCredentials(accesKey,
                                                                                                  accessSecret)),
                                         duration);
    }

    public BasicSessionCredentials getRoleSessionCredentials(String roleArn,
                                                             String region,
                                                             String roleSessionName,
                                                             AWSCredentialsProvider credentialProvider,
                                                             int duration)
            throws CouldNotGetSessionCredentialsException
    {
        AssumeRoleRequest roleRequest = new AssumeRoleRequest().withRoleArn(roleArn)
                                                               .withRoleSessionName(roleSessionName);

        return assumeRoleAndGetCredentials(roleRequest, credentialProvider, region, duration);
    }

    public BasicSessionCredentials getRoleSessionCredentialsWithMfa(String roleArn,
                                                                    String region,
                                                                    String roleSessionName,
                                                                    String serialMfa,
                                                                    String mfaCode,
                                                                    String accesKey,
                                                                    String accessSecret,
                                                                    int duration)
            throws CouldNotGetSessionCredentialsException
    {
        AssumeRoleRequest roleRequest = new AssumeRoleRequest().withRoleArn(roleArn)
                                                               .withSerialNumber(serialMfa)
                                                               .withTokenCode(mfaCode)
                                                               .withRoleSessionName(roleSessionName);

        return assumeRoleAndGetCredentials(roleRequest,
                                           new AWSStaticCredentialsProvider(new BasicAWSCredentials(accesKey,
                                                                                                    accessSecret)),
                                           region,
                                           duration);
    }

    public BasicSessionCredentials getRoleSessionCredentialsWithMfa(String roleArn,
                                                                    String region,
                                                                    String roleSessionName,
                                                                    String serialMfa,
                                                                    String mfaCode,
                                                                    AWSCredentialsProvider credentialProvider,
                                                                    int duration)
            throws CouldNotGetSessionCredentialsException
    {
        AssumeRoleRequest roleRequest = new AssumeRoleRequest().withRoleArn(roleArn)
                                                               .withSerialNumber(serialMfa)
                                                               .withTokenCode(mfaCode)
                                                               .withRoleSessionName(roleSessionName);

        return assumeRoleAndGetCredentials(roleRequest, credentialProvider, region, duration);
    }

    private BasicSessionCredentials assumeRoleAndGetCredentials(AssumeRoleRequest assumeRoleRequest,
                                                                AWSCredentialsProvider credentialProvider,
                                                                String region,
                                                                int duration)
            throws CouldNotGetSessionCredentialsException
    {
        AWSSecurityTokenService stsClient = AWSSecurityTokenServiceClientBuilder.standard()
                                                                                .withCredentials(credentialProvider)
                                                                                .withRegion(region)
                                                                                .build();

        AssumeRoleResult assumeResult = stsClient.assumeRole(assumeRoleRequest);
        if (assumeResult.getSdkHttpMetadata().getHttpStatusCode() != 200) {
            throw new CouldNotGetSessionCredentialsException("Could not get session token.");
        }

        return new BasicSessionCredentials(assumeResult.getCredentials().getAccessKeyId(),
                                           assumeResult.getCredentials().getSecretAccessKey(),
                                           assumeResult.getCredentials().getSessionToken());
    }

}
