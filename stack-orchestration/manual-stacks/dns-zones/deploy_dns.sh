ENVIRONMENT=${1}

sam deploy --stack-name rp-dns-zones-$ENVIRONMENT \
  --template-file template.yaml \
  --parameter-overrides Environment=$ENVIRONMENT \
  --tags \
    Source=govuk-one-login/relying-party-stub \
    Owner=di-orchestration@digital.cabinet-office.gov.uk