package br.com.ronybrand.orderapi.commons.security;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import java.io.IOException;
import java.util.List;

/**
 * Jackson-side safety net: if a class with a {@link Sensitive} field ever ends up being
 * serialized directly (the project convention is that only DTOs are serialized, never entities),
 * that property comes out masked instead of the real value - doesn't depend on anyone
 * remembering to exclude the field manually.
 */
public class SensitiveFieldsModule extends SimpleModule {

    private static final String MASK = "***REDACTED***";

    private static final JsonSerializer<Object> MASKING_SERIALIZER = new JsonSerializer<>() {
        @Override
        public void serialize(final Object value, final JsonGenerator gen, final SerializerProvider serializers) throws IOException {
            gen.writeString(MASK);
        }
    };

    @Override
    public void setupModule(final SetupContext context) {
        super.setupModule(context);
        context.addBeanSerializerModifier(new BeanSerializerModifier() {
            @Override
            public List<BeanPropertyWriter> changeProperties(final SerializationConfig config,
                    final BeanDescription beanDesc, final List<BeanPropertyWriter> beanProperties) {
                beanProperties.stream()
                        .filter(writer -> writer.getAnnotation(Sensitive.class) != null)
                        .forEach(writer -> writer.assignSerializer(MASKING_SERIALIZER));
                return beanProperties;
            }
        });
    }
}
