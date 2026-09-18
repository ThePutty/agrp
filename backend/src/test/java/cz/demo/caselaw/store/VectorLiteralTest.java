package cz.demo.caselaw.store;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VectorLiteralTest {

    @Test
    void formatsPgvectorLiteral() {
        assertThat(JdbcChunkRepository.toVectorLiteral(new float[]{0.1f, -0.25f, 3f})).isEqualTo("[0.1,-0.25,3.0]");
        assertThat(JdbcChunkRepository.toVectorLiteral(new float[0])).isEqualTo("[]");
        assertThat(JdbcChunkRepository.toVectorLiteral(null)).isNull();
    }
}
