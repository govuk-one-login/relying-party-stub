# Relying Party Stub

## Summary
This stub allows you to create a mock relying party to initiate a GOV.UK One Login journey.
It loads a screen where you can set various parameters (see [here]("https://docs.sign-in.service.gov.uk/integrate-with-integration-environment/")), and make calls to the `/authorize` endpoint using these settings.
Once you have gone through the authentication/identity journey, you will be redirected to the stub, with a page
displaying the user info returned by the One Login service.

## Local Setup
You'll need to set up environment variables to run locally. Make a copy of the `.env.template` file, and rename it `.env`.

The stub is currently set up to connect to the build environment (as sandpit blocks requests from localhost). Once you 
have a copy of `.env`, you'll need to go into the `build-client-registry` table in DynamoDB, and find the client with ClientName `di-auth-stub-relying-party-build` or `di-auth-stub-relying-party-build-app` for Doc App.
Update the value of `CLIENT_ID` to the ClientID of this client.

You will also need to set up the private key similarly. You can find this in Secrets Manager in the di-ipv-stubs-build
AWS account, under `/stubs/rp-build/CLIENT_PRIVATE_KEY` or `/stubs/rp-build/DOC_APP_CLIENT_PRIVATE_KEY` for Doc App.

If you want to run the Doc App stub locally, you should also set the `CLIENT_TYPE` variable to `app`.

To run the stub locally, run `docker compose up`.

It will be accessible on port 8080.

## Deploy To Dev
Note that we have renamed the `sandpit` environment to `dev` to align with secure pipelines. It will link up to the sandpit API.

Push your code to GitHub, and run the `Deploy dev` workflow. This should deploy your code to the dev environment.

## Deploy to other environments
Deployment to build, integration, staging and production is through secure pipelines. On merge to main, the stub will
deploy to build, then if successful promote to staging, then if successful promote to integration and production.

## Adding secrets

IPV have a tool that manages secrets in the stubs AWS account. If you need to add any further Secrets Manager secrets, 
you should add the name of the secret to the externally managed list [here](https://github.com/govuk-one-login/ipv-stubs-common-infra/blob/main/utils/config-mgmt/app/configs/stubs.build.params.yaml) for non prod,
or [here](https://github.com/govuk-one-login/ipv-stubs-common-infra/blob/main/utils/config-mgmt/app/configs/stubs.production.params.yaml) for prod.

## Deploying a new copy of the stub

At the moment, to deploy a new copy of the stub you should duplicate the resources in the template (except the lambda authorizer,
KMS key and Fargate cluster resources). You can see any example of how this is done by looking at the Performance Testing resources
in `template.yaml` in the root directory.

If you would like to deploy a new copy of the stub, you'll need to add it to the client registry for the relevant environment.
The way this is currently done is by adding a DynamoDB entry to the terraform in the `authentication-api` repository, in
the file `{environment}-stub-clients.tfvar` for all environments except dev (for example [for build](https://github.com/govuk-one-login/authentication-api/blob/2e2b4317f4cb0272f74473149aff730bbc844650/ci/terraform/shared/build-stub-clients.tfvars#L101)).
For the dev environment, add an entry [here](https://github.com/govuk-one-login/authentication-api/blob/7dfd3fdeed4cb9358c234d95ee0e0ea4852ca57d/ci/terraform/shared/sandpit.tfvars#L9).

Once you deploy this terraform, find the terraform state file in S3 for the shared module for the relevant environment.
Take a look at the outputs of this file, specifically `stub_rp_client_credentials`, and you'll find the private key for
your new client in PEM format. Search the file for this string, and you'll be able to find the private key in PKCS8 format.
You can check you have the right format as it will start with `-----BEGIN PRIVATE KEY-----`. You can then update the secret to be this value.

Note that when you're deploying a new copy of the stub for the first time, ECS will not deploy until all secrets have a value set - 
ie you have a set a value for the private key in secrets manager, so you will have to fetch this private key during the deployment, after the
secrets manager resource has been created.
