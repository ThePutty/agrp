package cz.demo.caselaw.search;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextNormalizerTest {

    @Test
    void stripsDiacriticsLowercasesAndCollapsesWhitespace() {
        assertThat(TextNormalizer.normalize("  Bezdůvodné   OBOHACENÍ\n\tžalovaného ")).isEqualTo("bezduvodne obohaceni zalovaneho");
    }

    @Test
    void unifiesQuotesDashesAndNonBreakingSpaces() {
        assertThat(TextNormalizer.normalize("„citace“ – a b")).isEqualTo("\"citace\" - a b");
    }

    @Test
    void removesSimpleMarkup() {
        assertThat(TextNormalizer.normalize("<b>I. Žaloba</b> se zamítá")).isEqualTo("i. zaloba se zamita");
    }

    @Test
    void handlesNullAndEmpty() {
        assertThat(TextNormalizer.normalize(null)).isEmpty();
        assertThat(TextNormalizer.normalize("   ")).isEmpty();
    }
}
