#!/usr/bin/env bash

set -eu
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
API_DIR="${DIR}/../di-authentication-api"
CLIENT_NAME=di-auth-stub-relying-party-sandpit


function usage() {
  cat <<USAGE
  A script to deploy the GOV.UK Sign in APIs to the sandpit environment.
  Requires a GDS CLI, AWS CLI and jq installed and configured.

  Usage:
    $0 [-c|--clean]

  Options:
    -c, --clean               run gradle clean before build
USAGE
}

CLEAN=""
while [[ $# -gt 0 ]]; do
  case $1 in
    -c|--clean)
      CLEAN="clean"
      ;;
    *)
      usage
      exit 1
      ;;
  esac
  shift
done

pushd "${DIR}" > /dev/null

echo "Building deployment..."
./gradlew ${CLEAN} build distZip

echo -n "Getting AWS credentials ... "
eval $(gds aws digital-identity-dev -e)
echo "done!"

echo -n "Getting config values ... "
pushd "${API_DIR}/ci/terraform/shared" > /dev/null
PRIVATE_KEY="$(terraform output -json | jq -r 'first(.stub_rp_client_credentials.value[] | select (.client_name == $client_name)).private_key' --arg client_name "${CLIENT_NAME}")"

cat <<EOF > "/tmp/sandpit-vars.yml"
op_base_url: https://api.sandpit.auth.ida.digital.cabinet-office.gov.uk
am_url: https://account-managment..sandpit.auth.ida.digital.cabinet-office.gov.uk
client_private_key: $(echo "${PRIVATE_KEY}" | openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt | jq -sR)
client_id: $(terraform output -json | jq 'first(.stub_rp_client_credentials.value[] | select (.client_name == $client_name)).client_id' --arg client_name "${CLIENT_NAME}")
app_name: ${CLIENT_NAME}
service_name: "Sample Service - Sandpit"
client_type: WEB
environment_name: sandpit
EOF
popd > /dev/null
echo "done!"

cf target -o gds-digital-identity-authentication -s sandpit

echo "Pushing..."
cf push --vars-file /tmp/sandpit-vars.yml

popd > /dev/null
