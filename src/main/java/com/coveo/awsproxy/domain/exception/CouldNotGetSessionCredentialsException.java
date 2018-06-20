package com.coveo.awsproxy.domain.exception;

public class CouldNotGetSessionCredentialsException extends Exception
{
    private static final long serialVersionUID = 6816419601123087693L;

    public CouldNotGetSessionCredentialsException(String reason)
    {
        super(reason);
    }

}
