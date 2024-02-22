# Manual stacks - DNS
## Intro

The SAM template creates a Record and certificate for `<subdomain>.stubs.account.gov.uk`
or `<subdomain>.<environment>.stubs.account.gov.uk` if environment is not `production`.

This Stack is deployed manually once per environment
as part of the DNS set up process. The production stack lives in `di-ipv-stubs-prod`
and all non-production environments live in `di-ipv-stubs-build` .

The records are created in a hosted zone owned by IPV. They also reference API gateways in the
[template file](../../../template.yaml). 

### Domains

The template creates records for the following subdomain(s):

- `rp`
- `doc-app-rp`
- `acceptance-test-rp` for build only
- `perf-test-rp` for staging only

## Deployment

Login into AWS with SSO on the browser. Choose an account, and select `Command line or programmatic access`. In your
terminal, run `aws configure sso` and enter the start URL and region from AWS on your browser. This will create a
profile that you can set as an environment variable, by running `export AWS_PROFILE=<profile>`.

After this you can then run the below, replacing `<environment>`with one
of `dev`, `build`, `staging`, `integration`, `production`:

```shell
./deploy_dns.sh <environment>
```
