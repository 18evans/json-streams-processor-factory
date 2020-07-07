package evans18.assignment.streamprocessorfactory.impl.util;

import evans18.assignment.streamprocessorfactory.impl.PickingEventProcessorFactoryTest;

/**
 * Helper methods for preparing Strings within the tests of the local {@link PickingEventProcessorFactoryTest}.
 */
public class TestStringUtil {

    /**
     * @param line      to be on set on each line
     * @param lineCount how many lines will be contained in the string
     * @param separator delimiter between each line (ie. '\n', '\r' or '\r\n')
     * @return - built string from the given ruleset.
     */
    public static String multiplyString(final String line, int lineCount, String separator) {
        final StringBuilder stringBuilder = new StringBuilder(line);
        for (int i = 0; i < lineCount - 1; i++) { //decrement because already with 1 appended
            stringBuilder.append(separator).append(line);
        }
        return stringBuilder.toString();
    }

    /**
     * String with multiple lines of {@param line} with chosen {@param separator} as
     * newline separator between lines.
     */
    public static String multiplyString(final String line, int lineCount, char separator) {
        return multiplyString(line, lineCount, String.valueOf(separator));
    }

    /**
     * String with multiple lines of {@param line} with '\n' as default
     * separator between lines.
     */
    public static String multiplyString(final String line, int lineCount) {
        return multiplyString(line, lineCount, '\n');
    }
}
