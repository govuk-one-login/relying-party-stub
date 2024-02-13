# di-auth-stub-relying party

## Summary
This stub allows you to set various paramaters (see [here]("https://docs.sign-in.service.gov.uk/integrate-with-integration-environment/")) and mock calls from an RP.

## Local Setup
You'll need to set up environment variables to run locally. Make a copy of the `.env.template` file, and rename it `.env`.
The stub is currently set up to connect to the build environment (as sandpit blocks requests from localhost). Once you 
have a copy of `.env`, you'll need to go into the `build-client-registry` table in DynamoDB, and find the client with ClientName `di-auth-stub-relying-party-build`.
Update the value of `CLIENT_ID` to the ClientID of this client.

To run the stub locally, run `docker compose up`.

It will be accessible on port 8080.

## Deploy To Dev
Note that we have renamed the `sandpit` environment to `dev` to align with secure pipelines. It will link up to the sandpit API.

Push your code to GitHub, and run the `Deploy dev` workflow. This should deploy your code to the dev environment.

## Adding secrets

IPV have a tool that manages secrets in the stubs AWS account. If you need to add any further Secrets Manager secrets, 
you should add the name of the secret to the externally managed list [here](https://github.com/govuk-one-login/ipv-stubs-common-infra/blob/main/utils/config-mgmt/app/configs/stubs.build.params.yaml) for non prod,
or [here](https://github.com/govuk-one-login/ipv-stubs-common-infra/blob/main/utils/config-mgmt/app/configs/stubs.production.params.yaml) for prod.