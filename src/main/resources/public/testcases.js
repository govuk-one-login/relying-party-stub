$(document).ready(function() {
    var credentialSubject = null;
    var evidence = null;
    $("#identity-options").hide()

    function updatejson() {
        if (credentialSubject !== null && evidence !==null) {
            $("#claims-inherited-identity").val("\"type\": [\"VerifiableCredential\",\"IdentityCheckCredential\"], \n" + "\"credentialSubject\": " + credentialSubject + ",\n\"evidence\": " + evidence);
        } else {
            $("#claims-inherited-identity").val("");
        }
    }

    $.getJSON('./data/inheritedJWTCredentialSubjects.json').then(function(dropdownData) {
        var select = $('<select class="govuk-select" id="test_data" name="sort">')
        select.append($("<option selected value='No inherited identity'>").text('No inherited identity'))
        $.each(dropdownData, function(key,value) {
            select.append($("<option>")
            .prop('value', value.label)
            .text(value.label));
        });

        $('#test_data_block').append(select);
        $("#test_data").on("change", function(event) {
            var val = $(event.target).val();
            if (val == "No inherited identity") {
                $('#custom_evidence_block option[selected="selected"]').each(
                    function() {
                        $(this).removeAttr('selected');
                    }
                );
                $("#custom_evidence_block option:first").attr('selected','selected');
                credentialSubject = null;
                evidence = null;
                $("#identity-options").hide();
            } else {
                var {payload} = dropdownData.find(x => x.label === val);
                credentialSubject = JSON.stringify(payload, undefined, 4);
                $("#identity-options").show();
            }
            updatejson();
        });
    });

    $.getJSON('./data/inheritedJWTEvidences.json').then(function(dropdownData) {
        select = $('<select class="govuk-select" id="custom_evidence_block" name="sort2">')
        select.append($("<option selected disabled hidden value=''>").text('Select from dropdown...'))
        $.each(dropdownData, function(key,value) {
            select.append($("<option>")
            .prop('value', value.label)
            .prop('id', value.scenario)
            .text(value.label));
        });

        $('#custom_evidence_block').append(select);
        $("#custom_evidence_block").on("change", function(event) {
            var val = $(event.target).val();
            var {payload} = dropdownData.find(x => x.label === val);
            evidence = JSON.stringify(payload, undefined, 4)
            updatejson();
        });
    });
});