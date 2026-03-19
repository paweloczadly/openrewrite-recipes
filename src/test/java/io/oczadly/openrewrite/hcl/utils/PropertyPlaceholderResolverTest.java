package io.oczadly.openrewrite.hcl.utils;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static io.oczadly.openrewrite.hcl.utils.SystemPropertyTestSupport.restoreSystemProperty;

class PropertyPlaceholderResolverTest {

    @Test
    void shouldReturnSameValueWhenNoPlaceholderExists() {
        String result = PropertyPlaceholderResolver.resolve("module.private_dns_zone.resource");

        assertThat(result).isEqualTo("module.private_dns_zone.resource");
    }

    @Test
    void shouldResolveSinglePlaceholderFromCustomProperties() {
        Properties properties = new Properties();
        properties.setProperty("avm.dns.module", "my_custom_dns");

        String result = PropertyPlaceholderResolver.resolve("module.${avm.dns.module}.resource", properties);

        assertThat(result).isEqualTo("module.my_custom_dns.resource");
    }

    @Test
    void shouldResolveDefaultWhenPropertyMissing() {
        Properties properties = new Properties();

        String result = PropertyPlaceholderResolver.resolve("module.${avm.dns.module:private_dns}.resource", properties);

        assertThat(result).isEqualTo("module.private_dns.resource");
    }

    @Test
    void shouldPreferPropertyOverDefaultWhenAvailable() {
        Properties properties = new Properties();
        properties.setProperty("avm.dns.module", "my_custom_dns");

        String result = PropertyPlaceholderResolver.resolve("module.${avm.dns.module:private_dns}.resource", properties);

        assertThat(result).isEqualTo("module.my_custom_dns.resource");
    }

    @Test
    void shouldResolveMultiplePlaceholdersInOneValue() {
        Properties properties = new Properties();
        properties.setProperty("env", "prod");
        properties.setProperty("region", "eastus2");

        String result = PropertyPlaceholderResolver.resolve("${env}-${region}", properties);

        assertThat(result).isEqualTo("prod-eastus2");
    }

    @Test
    void shouldThrowWhenRequiredPropertyIsMissingWithoutDefault() {
        Properties properties = new Properties();

        assertThatThrownBy(() -> PropertyPlaceholderResolver.resolve("${avm.missing.property}", properties))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to resolve property placeholders in: '${avm.missing.property}'")
            .hasMessageContaining("unresolved keys: avm.missing.property");
    }

    @Test
    void shouldIncludeAllUnresolvedKeysInErrorMessage() {
        Properties properties = new Properties();
        properties.setProperty("env", "prod");

        assertThatThrownBy(() -> PropertyPlaceholderResolver.resolve("${env}-${region}-${zone}", properties))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to resolve property placeholders in: '${env}-${region}-${zone}'")
            .hasMessageContaining("unresolved keys: region, zone");
    }

    @Test
    void shouldDeduplicateUnresolvedKeysInErrorMessage() {
        Properties properties = new Properties();

        assertThatThrownBy(() -> PropertyPlaceholderResolver.resolve("${region}-${region}", properties))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to resolve property placeholders in: '${region}-${region}'")
            .hasMessageContaining("unresolved keys: region");
    }

    @Test
    void shouldThrowWhenPlaceholderSyntaxIsInvalid() {
        Properties properties = new Properties();

        assertThatThrownBy(() -> PropertyPlaceholderResolver.resolve("${outer${inner}}", properties))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to resolve property placeholders in: '${outer${inner}}'");
    }

    @Test
    void shouldResolvePlaceholderWhenValueIsTerraformExpression() {
        // Reproduces the exact bug: -Davm.vnet.parent_id='${data.terraform_remote_state.rg.outputs.resource.id}'
        // The resolved value itself contains ${...} Terraform syntax — it must be treated as a literal string.
        Properties properties = new Properties();
        properties.setProperty("avm.vnet.parent_id", "${data.terraform_remote_state.rg.outputs.resource.id}");

        String result = PropertyPlaceholderResolver.resolve("${avm.vnet.parent_id}", properties);

        assertThat(result).isEqualTo("${data.terraform_remote_state.rg.outputs.resource.id}");
    }

    @Test
    void shouldInterpolatePlaceholderWithLiteralSuffixWhenValueIsTerraformExpression() {
        // ${avm.vnet.parent_id}pawel → "${data.terraform_remote_state.rg.outputs.resource.id}pawel"
        Properties properties = new Properties();
        properties.setProperty("avm.vnet.parent_id", "${data.terraform_remote_state.rg.outputs.resource.id}");

        String result = PropertyPlaceholderResolver.resolve("${avm.vnet.parent_id}pawel", properties);

        assertThat(result).isEqualTo("${data.terraform_remote_state.rg.outputs.resource.id}pawel");
    }

    @Test
    void shouldUseSystemPropertiesWhenPropertiesArgumentIsNull() {
        String key = "avm.placeholder.system";
        String value = "resolved-system-value";
        String previousValue = System.getProperty(key);
        System.setProperty(key, value);

        try {
            String result = PropertyPlaceholderResolver.resolve("${avm.placeholder.system}", null);
            assertThat(result).isEqualTo("resolved-system-value");
        } finally {
            restoreSystemProperty(key, previousValue);
        }
    }

}

