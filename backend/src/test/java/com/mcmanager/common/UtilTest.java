package com.mcmanager.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class UtilTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void blankRecognizesNullEmptyAndWhitespace() {
        assertThat(Util.blank(null)).isTrue();
        assertThat(Util.blank("")).isTrue();
        assertThat(Util.blank("  \t")).isTrue();
        assertThat(Util.blank(" a ")).isFalse();
    }

    @Test
    void textReturnsOnlyNonBlankValues() throws Exception {
        JsonNode n = mapper.readTree("{\"s\":\"v\",\"blank\":\" \",\"nul\":null,\"num\":5,\"obj\":{}}");
        assertThat(Util.text(null, "s")).isNull();
        assertThat(Util.text(n, "s")).isEqualTo("v");
        assertThat(Util.text(n, "num")).isEqualTo("5");
        assertThat(Util.text(n, "missing")).isNull();
        assertThat(Util.text(n, "nul")).isNull();
        assertThat(Util.text(n, "obj")).isNull();
        assertThat(Util.text(n, "blank")).isNull();
    }

    @Test
    void partitionSplitsIntoFixedSizeParts() {
        assertThat(Util.partition(List.of(1, 2, 3, 4, 5), 2)).containsExactly(List.of(1, 2), List.of(3, 4), List.of(5));
        assertThat(Util.partition(List.of(1, 2), 2)).containsExactly(List.of(1, 2));
        assertThat(Util.partition(List.<Integer>of(), 2)).isEmpty();
    }
}
