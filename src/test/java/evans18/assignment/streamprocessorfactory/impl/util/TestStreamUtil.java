package evans18.assignment.streamprocessorfactory.impl.util;

import javax.annotation.Nonnull;
import javax.annotation.WillNotClose;
import java.io.ByteArrayInputStream;

public class TestStreamUtil {

    /**
     * Helper method Converts {@link String} to {@link ByteArrayInputStream} containing param's bytes.
     *
     * @param textToConvert - text to convert from.
     * @return {@link ByteArrayInputStream}
     */
    @WillNotClose
    public static ByteArrayInputStream getInputStreamFromString(@Nonnull String textToConvert) {
        return new ByteArrayInputStream(textToConvert.getBytes());
    }

}
