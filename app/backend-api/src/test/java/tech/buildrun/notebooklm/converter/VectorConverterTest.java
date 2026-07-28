package tech.buildrun.notebooklm.converter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VectorConverterTest {

    private final VectorConverter converter = new VectorConverter();

    @Test
    void convertsFloatArrayToDatabaseColumn() {
        var result = converter.convertToDatabaseColumn(new float[] {0.1f, 0.2f, 0.3f});

        assertThat(result).isEqualTo("[0.1,0.2,0.3]");
    }

    @Test
    void convertsNullArrayToNullColumn() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertsDatabaseColumnToFloatArray() {
        var result = converter.convertToEntityAttribute("[0.1,0.2,0.3]");

        assertThat(result).containsExactly(0.1f, 0.2f, 0.3f);
    }

    @Test
    void convertsNullColumnToNullArray() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void convertsEmptyVectorToEmptyArray() {
        assertThat(converter.convertToEntityAttribute("[]")).isEmpty();
    }
}
