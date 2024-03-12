# Relying Party Stub

## Summary
This stub allows you to create a mock relying party to initiate a GOV.UK One Login journey.
It loads a screen where you can set various parameters (see [here]("https://docs.sign-in.service.gov.uk/integrate-with-integration-environment/")), and make calls to the `/authorize` endpoint using these settings.
Once you have gone through the authentication/identity journey, you will be redirected to the stub, with a page
displaying the user info returned by the One Login service.

## Local Setup
You'll need to set up environment variables to run locally. Make a copy of the `.env.template` file, and rename it `.env`.

You will also need to set up your local configuration. Make a copy of the `config.json.template` file named `config.json`.
You can either manually update the config.json file with the private keys for the clients, or you can retrieve a full 
configuration file from secrets manager in the di-ipv-stubs-build AWS account, under `/stubs/rp-build/CONFIGURATION`.

We typically run the stub against the build environment of One Login, as this has firewall rules to permit localhost redirect URLS.

To run the stub locally, run `docker compose up --build`.

It will be accessible on port 8080.

## Configuration

An instance of the stub is configured using a JSON blob, which allows it to act as a number of different relying
parties.
The `CONFIGURATION_SOURCE` environment variable contains the name or ARN of the AWS Secrets Manager secret which
contains this JSON blob.

When running locally, the `CONFIGURATION_SOURCE` environment variable is set to the special value `local`, which means
the configuration is read from the `config.json` file

## Deploy To Dev
Note that we have renamed the `sandpit` environment to `dev` to align with secure pipelines. It will link up to the sandpit API.

Push your code to GitHub, and run the `Deploy dev` workflow. This should deploy your code to the dev environment.

## Deploy to other environments
Deployment to build, integration, staging and production is through secure pipelines. On merge to main, the stub will
deploy to build, then if successful promote to staging, then if successful promote to integration and production.

## Adding secrets

We shouldn't need to add any more secrets manager secrets, as everything can be configured using the CONFIGURATION JSON
blob.

If we do, however, it's important to know that IPV have a tool that manages secrets in the stubs AWS account. 
If you need to add any further Secrets Manager secrets, 
you should add the name of the secret to the externally managed list [here](https://github.com/govuk-one-login/ipv-stubs-common-infra/blob/main/utils/config-mgmt/app/configs/stubs.build.params.yaml) for non prod,
or [here](https://github.com/govuk-one-login/ipv-stubs-common-infra/blob/main/utils/config-mgmt/app/configs/stubs.production.params.yaml) for prod.

## Adding a relying party

The configuration of an existing stub can be updated to include an additional relying party. This can be done by
updating the JSON blob in the relevant Secrets Manager secret in the correct IPV stubs AWS account (di-ipv-stubs-build
or di-ipv-stubs-prod).

To configure an additional relying party in One Login, add a DynamoDB entry to the terraform in the `authentication-api`
repository, in the
file `{environment}-stub-clients.tfvar` for all environments except dev (for
example [for build](https://github.com/govuk-one-login/authentication-api/blob/2e2b4317f4cb0272f74473149aff730bbc844650/ci/terraform/shared/build-stub-clients.tfvars#L101)).
For the dev environment, add an
entry [here](https://github.com/govuk-one-login/authentication-api/blob/7dfd3fdeed4cb9358c234d95ee0e0ea4852ca57d/ci/terraform/shared/sandpit.tfvars#L9).
If `test_client` is set to true, notifications are not sent through Notify.

Once you deploy this terraform, find the terraform state file in S3 for the shared module for the relevant environment.
Take a look at the outputs of this file, specifically `stub_rp_client_credentials`, and you'll find the private key for
your new client in PEM format. Search the file for this string, and you'll be able to find the private key in PKCS8
format.
You can check you have the right format as it will start with `-----BEGIN PRIVATE KEY-----`.


## Deploying a new copy of the stub

To deploy a new copy of the stub you should create new copies of the resources in the template (except the lambda authorizer,
KMS key, task role, and Fargate cluster resources). You can see any example of how this is done by looking at the Performance Testing resources
in `template.yaml` in the root directory.

Note that when you're deploying a new copy of the stub for the first time, ECS will not deploy until all secrets have a value set - 
i.e. you have a set a value for the private key in secrets manager, so you will have to fetch this private key during the deployment, after the
secrets manager resource has been created.

You'll probably also want to set up DNS for the stub. This is set up in a different template, as it can't currently be
deployed with secure pipelines. The template for this is in `stack-orchestration/manual-stacks/dns-zones`. Once you've
updated, redeploy with the instructions in the adjacent README. Note that this should be deployed after the resources
are deployed.