# aws-signature-proxy
Simple reverse proxy to access a AWS URL secured by AWS Signature (AWS Elasticsearch kibana URL is one of them)

It supports temporary credentials (session token) with or without MFA device.


## Disclaimer
This project was made as part of a hackathon so it's rough around the edges, lacks validation, might contains bugs and have a couple of things hardcoded. Feel free to open a PR or an issue if you find anything.

## How to use
The proxy needs a couple of parameters before it can start. You can inject those properties in a yaml file or with command line argument.

### YAML configuration
Create a `application.yml` file next to the .jar file : 
```
server:
  port: 8888

aws: 
  accesskey: insert aws access key here
  secretaccesskey: insert secret access key here
  mfaserial: insert mfa serial here (optional)
  mfacode: insert mfa code here (optional)
  rolearn: insert the role arn you wish to assume (arn:aws:iam::<account>:role/<rolename>)
  region: insert region here
  endpoint: endpoint of your AWS service (ex: https://es.cluster.com). Do not include path or trailing slash.
  servicename: name of your AWS service (ex: es)
  
```

### Command line argument
```
java -jar aws-signature-proxy-0.0.1.jar
```
if you are using a MFA, don't forget to change the mfacode in the application.yml first.
The credentials will be valid for two hours after which you will have to restart the proxy. 
