package simple.repo.rpm.repomd.dto;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.Instant;

/**
 * for use with {@link JsonSerialize}/{@link JsonDeserialize}
 */
public class InstantSecondSerDe {
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @JacksonAnnotationsInside
    @JsonSerialize(using = Serializer.class)
    @JsonDeserialize(using = Deserializer.class)
    public @interface JsonSerDeEpochSecond {
    }

    public static class Serializer extends ValueSerializer<Instant> {
        @Override
        public void serialize(Instant value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
            gen.writeNumber(value.getEpochSecond());
        }
    }

    public static class Deserializer extends ValueDeserializer<Instant> {
        @Override
        public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
            return Instant.ofEpochSecond(p.getLongValue());
        }
    }
}
