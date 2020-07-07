package evans18.assignment.streamprocessorfactory.impl;

import com.google.gson.Gson;
import evans18.assignment.streamprocessorfactory.api.EventProcessorFactory;
import evans18.assignment.streamprocessorfactory.api.StreamProcessor;
import evans18.assignment.streamprocessorfactory.impl.util.TestStreamUtil;
import evans18.assignment.streamprocessorfactory.impl.util.TestStringUtil;
import org.json.JSONException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public final class PickingEventProcessorFactoryTest {

    /**
     * Example valid Json-formatted String that is one line.
     * Used as Light-weight example.
     * Note: identical to first line of the given {@value EXAMPLE_JSON_STREAMS_FILE_NAME} json file.
     */
    public static final String EXAMPLE_JSON_ONE_LINE_PICKER_EVENT = "{\"timestamp\":\"2018-12-20T11:50:48Z\",\"id\":\"2344\",\"picker\":{\"id\":\"14\",\"name\":\"Joris\",\"active_since\":\"2018-09-20T08:20:00Z\"},\"article\":{\"id\":\"13473\",\"name\":\"ACME Bananas\",\"temperature_zone\":\"ambient\"},\"quantity\":2}";
    private static final Gson gson = new Gson();
    private static final String EXAMPLE_JSON_STREAMS_FILE_NAME = "happy-path-input.json-stream";
    private static final int EXAMPLE_VALID_MAX_EVENTS = 5;
    private static final Duration EXAMPLE_VALID_DURATION = Duration.ofSeconds(3);
    private static final OutputStream EXAMPLE_VALID_NULL_OUTPUT_STREAM = OutputStream.nullOutputStream();

    /**
     * Close static streams.
     */
    @AfterAll
    static void tearDown() throws IOException {
        EXAMPLE_VALID_NULL_OUTPUT_STREAM.close();
    }

    private static Stream<Arguments> happyPathTestCaseInputProvider() {
        return Stream.of(
                Arguments.of(
                        100,
                        Duration.ofSeconds(30),
                        EXAMPLE_JSON_STREAMS_FILE_NAME,
                        "happy-path-output.json"));
    }

    private static Arguments[] createFactoryTestCaseProvider() {
        Iterator<EventProcessorFactory> factories = ServiceLoader.load(EventProcessorFactory.class).iterator();
        return new Arguments[]{
                Arguments.of(factories.next())
        };
    }

    /**
     * Returns valid processor with example valid parameters. Used for testing of the processor that is
     * non-specific to the passed parameters of {@link StreamProcessor#process(InputStream, OutputStream)}.
     * Relies on valid factory parameter from {@link #createFactoryTestCaseProvider()}.
     *
     * @return - valid processor taken from the validation test at  {@link #testFactoryCreateProcessorValid(EventProcessorFactory)} (last line).
     */
    private static Arguments[] createValidProcessorFromFactoryTestCaseProvider() {
        EventProcessorFactory factory = (EventProcessorFactory) createFactoryTestCaseProvider()[0].get()[0];
        return new Arguments[]{Arguments.of(factory.createProcessor(EXAMPLE_VALID_MAX_EVENTS, EXAMPLE_VALID_DURATION))
        };
    }

    @ParameterizedTest
    @MethodSource("happyPathTestCaseInputProvider")
    void testHappyPath(
            int maxEvents,
            Duration maxTime,
            String inputResource,
            String expectedOutputResource) {
        try {
            try (final EventProcessorFactory factory = new PickingEventProcessorFactory();
                 final StreamProcessor processor = factory.createProcessor(maxEvents, maxTime);
                 final InputStream source = getClass().getResourceAsStream(inputResource);
                 final ByteArrayOutputStream sink = new ByteArrayOutputStream()) {
                processor.process(source, sink);
                final String expectedOutput = loadResource(expectedOutputResource);
                final String actualOutput = new String(sink.toByteArray(), StandardCharsets.UTF_8);
                JSONAssert.assertEquals(expectedOutput, actualOutput, JSONCompareMode.STRICT);
            }
        } catch (IOException | JSONException ex) {
            fail("Unexpected exception.", ex);
        }
    }

    @ParameterizedTest
    @MethodSource("happyPathTestCaseInputProvider")
    void testHappyPathFromSystemInputSource(
            int maxEvents,
            Duration maxTime,
            String inputResource,
            String expectedOutputResource) {
        try (final EventProcessorFactory factory = new PickingEventProcessorFactory();
             final StreamProcessor processor = factory.createProcessor(maxEvents, maxTime);
             final InputStream sourceContents = getClass().getResourceAsStream(inputResource);
             final ByteArrayOutputStream sink = new ByteArrayOutputStream()) {

            System.setIn(sourceContents); //write file contents to System.in
            //todo note 1/2:    if System.in is to be reused, as long as sourceContents is closed,
            //todo note 2/2:    System.in will also be closed during program's lifecycle
            processor.process(System.in, sink);

            final String expectedOutput = loadResource(expectedOutputResource);
            final String actualOutput = new String(sink.toByteArray(), StandardCharsets.UTF_8);

            final Object[] jsonOutput = gson.fromJson(actualOutput, Object[].class);

            assertAll(
                    () -> //on json-stream valid one-line event input, output will not be empty collection
                            assertNotEquals(0, jsonOutput.length),
                    () -> //is expected
                            JSONAssert.assertEquals(expectedOutput, actualOutput, JSONCompareMode.STRICT),
                    () -> //assert - all of the bytes have been processed
                            assertEquals(0, sourceContents.available())
            );

        } catch (IOException ex) {
            fail("Unexpected IOException", ex);
        }
    }

    private String loadResource(String resource) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(resource);
             Scanner scanner = new Scanner(is, StandardCharsets.UTF_8)) {
            scanner.useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
        }
    }

    /**
     * Verifies that precisely one {@link EventProcessorFactory} can be service-loaded.
     */
    @Test
    void testServiceLoadingWithOnlyOneProcessor() {
        final Iterator<EventProcessorFactory> factories = ServiceLoader.load(EventProcessorFactory.class).iterator();
        assertTrue(factories.hasNext(), "No EventProcessorFactory is service-loaded");
        factories.next();
        assertFalse(factories.hasNext(), "More than one EventProcessorFactory is service-loaded");
        assertThrows(NoSuchElementException.class, factories::next);
    }

    @ParameterizedTest
    @MethodSource("createFactoryTestCaseProvider")
    void testCreateFactoryTestCaseProvider(EventProcessorFactory factory) {
        assertNotNull(factory);
    }

    /*
     * Region for testing the factory's StreamProcessor#process implementation
     */

    @ParameterizedTest
    @MethodSource("createFactoryTestCaseProvider")
    void testFactoryCreateProcessorValid(EventProcessorFactory factory) {
        try (factory) {
            //assert - negative maxEvents disallowed
            final int MAX_EVENT_NEGATIVE = -1;
            assertThrows(IllegalArgumentException.class, () -> factory.createProcessor(MAX_EVENT_NEGATIVE, null));

            //assert - null maxTime disallowed
            final int MAX_EVENT_POSITIVE = 1;
            assertThrows(NullPointerException.class, () -> factory.createProcessor(EXAMPLE_VALID_MAX_EVENTS, null));

            //assert - negative Duration value disallowed
            final Duration NEGATIVE_DURATION = Duration.ofDays(-1L);
            assertThrows(IllegalArgumentException.class, () -> factory.createProcessor(EXAMPLE_VALID_MAX_EVENTS, NEGATIVE_DURATION));

            //assert - valid
            assertAll(() -> //assert - non-throw on positive args
                            Assertions.assertDoesNotThrow(() -> factory.createProcessor(EXAMPLE_VALID_MAX_EVENTS, EXAMPLE_VALID_DURATION)),
                    () -> //assert - non null result
                            assertNotNull(factory.createProcessor(MAX_EVENT_POSITIVE, EXAMPLE_VALID_DURATION))
            );
        }
    }

    @ParameterizedTest
    @MethodSource("createFactoryTestCaseProvider")
    void testFactoryCreatesDistinctProcessors(EventProcessorFactory factory) {
        try (factory;
             var processorA = factory.createProcessor(EXAMPLE_VALID_MAX_EVENTS, EXAMPLE_VALID_DURATION);
             var processorB = factory.createProcessor(EXAMPLE_VALID_MAX_EVENTS, EXAMPLE_VALID_DURATION)
        ) {
            assertNotEquals(processorA, processorB);
        }
    }

    @ParameterizedTest
    @MethodSource("createValidProcessorFromFactoryTestCaseProvider")
    void processorWithNullInputOutputStreams(StreamProcessor processor) {
        try (processor) {
            //assert - null Streams params disallowed
            assertAll(
                    () ->
                            assertThrows(IllegalArgumentException.class, () -> processor.process(null, EXAMPLE_VALID_NULL_OUTPUT_STREAM)),
                    () ->
                            assertThrows(IllegalArgumentException.class, () -> processor.process(InputStream.nullInputStream(), null))
            );
        }
    }

    @ParameterizedTest
    @MethodSource("createValidProcessorFromFactoryTestCaseProvider")
    void processorInputForNonJsonSource(StreamProcessor processor) {
        final String jsonOutput;

        //setup - non-json contents file
        try (final InputStream inputStreamNonJson = getClass().getResourceAsStream("invalid-input.json-stream");
             final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             processor) {

            //assert - processing malformed json input throws IOException
            assertThrows(IOException.class, () -> processor.process(inputStreamNonJson, outputStream));

            //setup - read processed output
            jsonOutput = new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            fail("Unexpected IOException", ex);
            return;
        }

        //setup - parse json output into collection
        final Object[] emptyCollection = gson.fromJson(jsonOutput, Object[].class);

        //assert - on IOException -> output will be empty collection
        assertEquals(0, emptyCollection.length);
    }

    @ParameterizedTest
    @MethodSource("createValidProcessorFromFactoryTestCaseProvider")
    void processorWithInputOneLineJsonObject(StreamProcessor processor) {
        final String jsonOutput;

        //setup - json on one line
        try (final InputStream inputStreamMultipleLineJson = TestStreamUtil.getInputStreamFromString(EXAMPLE_JSON_ONE_LINE_PICKER_EVENT);
             final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             processor) {

            //setup - process
            processor.process(inputStreamMultipleLineJson, outputStream);

            //setup - output collection from output json
            jsonOutput = new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            fail("Unexpected IOException", ex);
            return;
        }

        //setup - output collection
        final Object[] arrOutput = gson.fromJson(jsonOutput, Object[].class);

        //assert - one line json is supported -> collection won't be empty on valid oneline json event input
        assertNotEquals(0, arrOutput.length);
    }

    @ParameterizedTest
    @MethodSource("createValidProcessorFromFactoryTestCaseProvider")
    void processorWithInputPrettyPrintJsonInput(StreamProcessor processor) {
        final String jsonOutputOneEventResult, jsonOutputMultipleEventResult;

        //setup - json (one or multiple) on multiple lines not on one line
        try (
                final InputStream inputStreamMultipleLineJson = getClass().getResourceAsStream("pick-event-on-multi-line.json");
                final InputStream inputStreamMultipleLineJsonObjects = getClass().getResourceAsStream("pick-events-on-multi-line.json-stream");
                final ByteArrayOutputStream outputStreamOne = new ByteArrayOutputStream();
                final ByteArrayOutputStream outputStreamMultiple = new ByteArrayOutputStream();
                processor) {

            //setup - pretty printed jsons (aka spread on multi line) disallowed
            assertAll(
                    () -> assertThrows(IOException.class, () ->
                            processor.process(inputStreamMultipleLineJson, outputStreamOne)
                    ),
                    () -> assertThrows(IOException.class, () ->
                            processor.process(inputStreamMultipleLineJsonObjects, outputStreamMultiple)
                    )
            );

            //setup - read processed output
            jsonOutputOneEventResult = new String(outputStreamOne.toByteArray(), StandardCharsets.UTF_8);
            jsonOutputMultipleEventResult = new String(outputStreamMultiple.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            fail("Unexpected IOException", ex);
            return;
        }

        //setup - parse output json into collections
        final Object[] emptyCollectionForInputOne = gson.fromJson(jsonOutputOneEventResult, Object[].class);
        final Object[] emptyCollectionForInputMultiple = gson.fromJson(jsonOutputMultipleEventResult, Object[].class);

        //assert - on IOException -> output will be empty collection
        assertEquals(0, emptyCollectionForInputOne.length);
        assertEquals(0, emptyCollectionForInputMultiple.length);
    }

    @ParameterizedTest
    @MethodSource("createValidProcessorFromFactoryTestCaseProvider")
    void processorWithKeepAliveMessages(StreamProcessor processor) {
        //setup - create count of newlines
        final int exactCount = 5;

        //setup - json object per line
        final String jsonOneLine = EXAMPLE_JSON_ONE_LINE_PICKER_EVENT;

        //setup - populate InputStream with keep-alive newline character messages
        try (final InputStream inputStreamFile = getClass().getResourceAsStream("events_with_keepactive_in_between.json-stream");
             final InputStream inputStreamLF = TestStreamUtil.getInputStreamFromString(TestStringUtil.multiplyString(jsonOneLine, exactCount, '\n'));  //example String: "{}\n{}\n{}\n{}"
             final InputStream inputStreamCR = TestStreamUtil.getInputStreamFromString(TestStringUtil.multiplyString(jsonOneLine, exactCount, '\r'));
             final InputStream inputStreamCRLF = TestStreamUtil.getInputStreamFromString(TestStringUtil.multiplyString(jsonOneLine, exactCount, "\r\n"));
             processor) {

            //setup - process the stream
            processor.process(inputStreamFile, EXAMPLE_VALID_NULL_OUTPUT_STREAM);
            processor.process(inputStreamLF, EXAMPLE_VALID_NULL_OUTPUT_STREAM);
            processor.process(inputStreamCR, EXAMPLE_VALID_NULL_OUTPUT_STREAM);
            processor.process(inputStreamCRLF, EXAMPLE_VALID_NULL_OUTPUT_STREAM);

            //assert - no bytes left
            assertEquals(0, inputStreamFile.available());
            assertEquals(0, inputStreamLF.available());
            assertEquals(0, inputStreamCR.available());
            assertEquals(0, inputStreamCRLF.available());
        } catch (IOException ex) {
            fail("Unexpected IOException", ex);
        }
    }

    /*
     * Region for testing createProcessor() parameters for maxCount & maxTime
     */

    @ParameterizedTest
    @MethodSource("createFactoryTestCaseProvider")
    @SuppressWarnings("UnnecessaryLocalVariable")
        //mute compiler warning
    void processorWithExactEventCountAsMaxCount(EventProcessorFactory factory) {
        //setup - factory creates processor with specific max event count
        final int maxEventCount = 5;
        final int exactCount = maxEventCount;
        assertEquals(exactCount, maxEventCount);

        //setup - populate json stream into InputStream
        try (final InputStream inputStream = TestStreamUtil.getInputStreamFromString(TestStringUtil.multiplyString(EXAMPLE_JSON_ONE_LINE_PICKER_EVENT, exactCount));
             final StreamProcessor processor = factory.createProcessor(maxEventCount, EXAMPLE_VALID_DURATION)
        ) {
            //setup - process the stream
            processor.process(inputStream, EXAMPLE_VALID_NULL_OUTPUT_STREAM);

            //assert - no bytes left
            assertEquals(0, inputStream.available());
        } catch (IOException ex) {
            fail("Unexpected IOException", ex);
        }
    }

    @ParameterizedTest
    @MethodSource("createFactoryTestCaseProvider")
    @SuppressWarnings("ConstantConditions")
        //mute compiler warning
    void processorWithEventCountOverMaxCount(EventProcessorFactory factory) {
        //setup - factory creates processor with maxEvent count lower than to-be-processed event count
        final StreamProcessor processor;
        final int exactCount;
        try (factory) {
            final int maxEventsCount = 3;
            exactCount = 5;
            assertTrue(exactCount > maxEventsCount);

            processor = factory.createProcessor(maxEventsCount, EXAMPLE_VALID_DURATION);
        }

        //setup - populate json stream into InputStream
        try (final InputStream inputStreamLF = TestStreamUtil.getInputStreamFromString(TestStringUtil.multiplyString(EXAMPLE_JSON_ONE_LINE_PICKER_EVENT, exactCount));
             final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             processor) {

            //setup - process the stream
            processor.process(inputStreamLF, outputStream);

            //assert - leftover bytes still exist in input streams content
            assertNotEquals(0, inputStreamLF.available());
        } catch (IOException ex) {
            fail("Unexpected IOException", ex);
        }

    }

    @ParameterizedTest
    @MethodSource("createFactoryTestCaseProvider")
    @SuppressWarnings("ConstantConditions")
        //mute compiler warning
    void processorWithEventCountUnderMaxCount(EventProcessorFactory factory) {
        //setup - creates processor with maxEvents higher than the to-be-processed event count
        final StreamProcessor processor;
        final int exactCount;
        try (factory) {
            final int maxEventsCount = 10;
            exactCount = 5;

            assertTrue(exactCount < maxEventsCount);
            processor = factory.createProcessor(maxEventsCount, EXAMPLE_VALID_DURATION);
        }

        //setup - populate json stream into InputStream
        try (final InputStream inputStream = TestStreamUtil.getInputStreamFromString(TestStringUtil.multiplyString(EXAMPLE_JSON_ONE_LINE_PICKER_EVENT, exactCount));
             processor) {
            //setup - process the stream
            processor.process(inputStream, EXAMPLE_VALID_NULL_OUTPUT_STREAM);

            //assert - all of the bytes have been processed
            assertEquals(0, inputStream.available());
        } catch (IOException ex) {
            fail("Unexpected IOException", ex);
        }
    }

    @ParameterizedTest
    @MethodSource("createFactoryTestCaseProvider")
    void processorRespectsFactoryMaxTime(EventProcessorFactory factory) {
        //setup - processor for N duration
        final Duration validMaxTime = Duration.ofSeconds(3); //note: do low time value so that test doesn't take too long
        final StreamProcessor processor;
        try (factory) {
            processor = factory.createProcessor(EXAMPLE_VALID_MAX_EVENTS, validMaxTime);
        }
        final long maxTimeInSeconds = validMaxTime.toSeconds(); //will be used for assert condition

        // setup - process will be run on separate cached thread so as to not block tester's thread
        final ExecutorService pool = Executors.newCachedThreadPool();
        final long[] millisStartAndEnd = new long[2]; //timestamps: [0] - start process, [1] - end process

        //setup - input & output
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             //input will be on stand-by
             InputStream inputStream = new InputStream() {
                 @Override
                 public int read() {
                     return 0; //stand-by by returning empty bytes
                 }
             };
             processor) {

            pool.submit(() -> {
                try {
                    synchronized (millisStartAndEnd) {
                        millisStartAndEnd[0] = System.currentTimeMillis();
                        processor.process(inputStream, outputStream);
                        millisStartAndEnd[1] = System.currentTimeMillis();
                        millisStartAndEnd.notify();
                    }
                } catch (IOException e) {
                    fail("Unexpected IOException", e);
                }
            });
        } catch (IOException e) {
            fail("Unexpected IOException", e);
        }

        synchronized (millisStartAndEnd) {
            try {
                millisStartAndEnd.wait();
            } catch (InterruptedException e) {
                fail("Unexpected InterruptedException", e);
            }

            final long processDurationSeconds = (millisStartAndEnd[1] - millisStartAndEnd[0]) / 1000; //wrap to only cast the long after subtraction

            assertTrue(processDurationSeconds <= maxTimeInSeconds, "Expected value " + processDurationSeconds + " was greater than " + maxTimeInSeconds);
        }
    }

}
