package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.google.common.collect.Iterables;

import org.apache.commons.lang3.LocaleUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.Errors;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.HasLang;
import org.sagebionetworks.bridge.models.Label;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.apps.PasswordPolicy;

public class ValidatorUtils {
    
    public static final String DUPLICATE_LANG = "%s is a duplicate message under the same language code";
    public static final String INVALID_LANG = "%s is not a valid ISO 639 alpha-2 or alpha-3 language code";

    public static boolean participantHasValidIdentifier(StudyParticipant participant) {
        Phone phone = participant.getPhone();
        String email = participant.getEmail();
        String anyExternalId = participant.getExternalIds().isEmpty() ? null : 
            Iterables.getFirst(participant.getExternalIds().entrySet(), null).getValue();
        String synapseUserId = participant.getSynapseUserId();
        return (email != null || isNotBlank(anyExternalId) || phone != null || isNotBlank(synapseUserId));
    }
    
    public static boolean accountHasValidIdentifier(Account account) {
        Phone phone = account.getPhone();
        String email = account.getEmail();
        String synapseUserId = account.getSynapseUserId();
        Set<String> externalIds = BridgeUtils.collectExternalIds(account);
        return (email != null || !externalIds.isEmpty() || phone != null || isNotBlank(synapseUserId));
    }

    public static void validatePassword(Errors errors, PasswordPolicy passwordPolicy, String password) {
        if (StringUtils.isBlank(password)) {
            errors.rejectValue("password", "is required");
        } else {
            if (passwordPolicy.getMinLength() > 0 && password.length() < passwordPolicy.getMinLength()) {
                errors.rejectValue("password", "must be at least "+passwordPolicy.getMinLength()+" characters");
            }
            if (passwordPolicy.isNumericRequired() && !password.matches(".*\\d+.*")) {
                errors.rejectValue("password", "must contain at least one number (0-9)");
            }
            if (passwordPolicy.isSymbolRequired() && !password.matches(".*\\p{Punct}+.*")) {
                errors.rejectValue("password", "must contain at least one symbol ( !\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~ )");
            }
            if (passwordPolicy.isLowerCaseRequired() && !password.matches(".*[a-z]+.*")) {
                errors.rejectValue("password", "must contain at least one lowercase letter (a-z)");
            }
            if (passwordPolicy.isUpperCaseRequired() && !password.matches(".*[A-Z]+.*")) {
                errors.rejectValue("password", "must contain at least one uppercase letter (A-Z)");
            }
        }
    }
    
    private static void validateLanguageSet(Errors errors, List<? extends HasLang> items, String fieldName) {
        if (BridgeUtils.isEmpty(items)) {
            return;
        }
        Set<String> visited = new HashSet<>();
        for (int i=0; i < items.size(); i++) {
            HasLang item = items.get(i);
            errors.pushNestedPath(fieldName + "[" + i + "]");

            if (isBlank(item.getLang())) {
                errors.rejectValue("lang", CANNOT_BE_BLANK);
            } else {
                if (visited.contains(item.getLang())) {
                    errors.rejectValue("lang", DUPLICATE_LANG);
                }
                visited.add(item.getLang());

                Locale locale = new Locale.Builder().setLanguageTag(item.getLang()).build();
                if (!LocaleUtils.isAvailableLocale(locale)) {
                    errors.rejectValue("lang", INVALID_LANG);
                }
            }
            errors.popNestedPath();
        }
    }
    
    public static void validateLabels(Errors errors, List<Label> labels) {
        if (labels == null || labels.isEmpty()) {
            return;
        }
        validateLanguageSet(errors, labels, "labels");    
        for (int j=0; j < labels.size(); j++) {
            Label label = labels.get(j);

            if (isBlank(label.getValue())) {
                errors.pushNestedPath("labels[" + j + "]");
                errors.rejectValue("value", CANNOT_BE_BLANK);
                errors.popNestedPath();
            }
        }
    }
}
