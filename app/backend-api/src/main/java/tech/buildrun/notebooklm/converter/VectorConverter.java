package tech.buildrun.notebooklm.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class VectorConverter implements AttributeConverter<float[], String> {

    @Override
    public String convertToDatabaseColumn(float[] attribute) {
        if (attribute == null) {
            return null;
        }
        var sb = new StringBuilder("[");
        for (int i = 0; i < attribute.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(attribute[i]);
        }
        return sb.append(']').toString();
    }

    @Override
    public float[] convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        var trimmed = dbData.substring(1, dbData.length() - 1);
        if (trimmed.isEmpty()) {
            return new float[0];
        }
        var parts = trimmed.split(",");
        var result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i]);
        }
        return result;
    }
}
