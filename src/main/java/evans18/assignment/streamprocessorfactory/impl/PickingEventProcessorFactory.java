package evans18.assignment.streamprocessorfactory.impl;

import com.google.auto.service.AutoService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import evans18.assignment.streamprocessorfactory.api.EventProcessorFactory;
import evans18.assignment.streamprocessorfactory.api.StreamProcessor;
import evans18.assignment.streamprocessorfactory.model.input.Article;
import evans18.assignment.streamprocessorfactory.model.input.Picker;
import evans18.assignment.streamprocessorfactory.model.input.PickerEvent;
import evans18.assignment.streamprocessorfactory.model.output.ArticlePick;
import evans18.assignment.streamprocessorfactory.model.output.PickerSummary;

import javax.annotation.Nonnull;
import javax.annotation.WillNotClose;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

@AutoService(EventProcessorFactory.class)
public final class PickingEventProcessorFactory implements EventProcessorFactory {

    /**
     * Parser tool for Json formatted objects encountered in {@link String}.
     */
    private static final Gson gson;

    static {
        final String PATTERN_DATE_SERIALIZATION = "yyyy-MM-dd'T'HH:mm:ss'Z'";
        gson = new GsonBuilder()
                .setDateFormat(PATTERN_DATE_SERIALIZATION)
                .setPrettyPrinting() //pretty-print (only toJson, which is used on Output)
                .create();
    }

    /**
     * Pool of cacheable/reusable threads used for submitting {@link Runnable} instances to.
     * <p>
     * Used for submitting the {@link StreamProcessor#process(InputStream, OutputStream)} task on.
     */
    private static final ExecutorService pool = Executors.newCachedThreadPool();
    /**
     * Comparator instance for {@link ArticlePick} instances most commonly expected
     * in {@link PickerSummary#picks} field of type {@link ArticlePick[]}.
     * <p>
     * Uses natural order comparison of {@link ArticlePick#getTimestamp()}.
     */
    private static final Comparator<ArticlePick> articlePickComparator = Comparator.comparing(ArticlePick::getTimestamp);
    /**
     * Comparator instance for {@link Picker} instances most commonly expected
     * in {@link PickerSummary} collections.
     * <p>
     * Uses natural order comparison of {@link Picker#getActiveSince()}
     * and if that is equal, then compares natural ordering of {@link Picker#getId()}.
     */
    private static final Comparator<Picker> pickerComparator = Comparator
            .comparing(Picker::getActiveSince)
            .thenComparing(Picker::getId);

    /**
     * Array of {@link String} the items of which points to the only
     * {@link Article} instances that will be processed to the output of
     * {@link StreamProcessor#process(InputStream, OutputStream)} according to
     * whether that {@link Article#temperatureZone} is contained in this array.
     */
    private static final String[] FILTERED_TEMPERATURE_ZONES = new String[]{
            Article.TEMPERATURE_ZONE_AMBIENT
    };

    /**
     * Helper "contains" check implementation for {@link #FILTERED_TEMPERATURE_ZONES}
     */
    private static boolean isTemperatureZoneInFilter(String temperatureZone) {
        return Arrays.asList(FILTERED_TEMPERATURE_ZONES).contains(temperatureZone);
    }

    /**
     * Helper method to convert the processed collection into the expected Output format.
     * First maps each item to the stripped-down model (ie. {@link PickerSummary}) then
     * converts the collection into a Json-formatted representation {@link String}.
     *
     * @param mapPickerSummaryRaw - collection to format.
     * @return the parsed collection as {@link String}. May be "{}" if nothing was processed.
     */
    private static String convertPickerSummaryCollectionToJson(@Nonnull Map<Picker, SortedSet<ArticlePick>> mapPickerSummaryRaw) {
        //  First Map each entry to the stripped-down model
        //  then convert entry collection to Json object format

        return gson.toJson(
                mapPickerSummaryRaw.entrySet()
                        .stream()
                        .map(pickerSummaryRaw -> {
                            final Picker picker = pickerSummaryRaw.getKey();
                            final var arrPicks = pickerSummaryRaw.getValue().toArray(ArticlePick[]::new);

                            return new PickerSummary(picker.name, picker.getActiveSince(), arrPicks);
                        })
                        .toArray(PickerSummary[]::new)
        );
    }

    /**
     * Finds the next line in the given {@link InputStream}'s remaining byte contents that is
     * a {@link String} formatted as a Json object parsing it into {@link PickerEvent} before returning it.
     * <p>
     * Newline characters, such as '\r' and '\n' are skipped over, including lines which are entirely made from them.
     * <p>
     * The {@link InputStream#read()} is called over until encountered is either the {@link PickerEvent} that
     * the method returns or the "end of stream" denoting integer (ie. '-1'), which would then have the method return null.
     *
     * <p>
     * Note: In the case of {@link PickerEvent} being reached the {@param source} {@link InputStream}'s next
     * position will be the byte at the index after the last called {@link InputStream#read()}. Consequently,
     * {@link InputStream#available()} will continue to point to the accurate available remaining bytes.
     *
     * @param source {@link InputStream} to read from.
     * @return - {@link PickerEvent} or {@code null} if no non-newline characters are present in given {@link InputStream}.
     * @throws JsonSyntaxException if formatted object was not on one line.
     * @throws IOException         if I/O error occurs during stream read or if read object cannot be parsed into a Json object
     *                             wrapping the {@link JsonSyntaxException} within an implicitly
     *                             thrown {@link IOException#IOException(String, Throwable)}.
     */
    @SuppressWarnings("StringConcatenationInLoop") //compiler warning mute
    private static PickerEvent parseNextOneLineJsonElementIntoPickerEvent(InputStream source) throws IOException {
        String line = "";

        //note use bracket counters to detect when to parse completed json object
        int countBracketOpen = 0; //'{'
        int countBracketClose = 0; //'}'

        int b;
        for (b = source.read(); b != -1; b = source.read()) {
            if (b == 0)
                continue; //skip empty byte

            final char c = (char) b;

            if (c == '\n' || c == '\r') { //new-line char
                line = ""; //reset line because json objects on a single line are ONLY allowed
                //also skip char
            } else {
                line += c;
                if (c == '{') countBracketOpen++;
                else if (c == '}') countBracketClose++;

                if (countBracketOpen == countBracketClose && !line.isBlank()) { //if json closed AND line so far is not empty
                    try {
                        return gson.fromJson(line, PickerEvent.class); //return parsed obj
                    } catch (JsonSyntaxException ex) {
                        throw new IOException("InputStream must have content where each line is either " +
                                "a valid Json-formatted PickerEvent or a new line char (ie. \"\\r\", \"\\n\" or \"\\r\\n\").", ex);
                    }
                }
            }
        }

        return null;  //reached end of stream
    }

    /**
     * Parses the passed {@param source} {@link InputStream}'s remaining byte contents
     * into the contents of the passed {@param mapPickerSummaryRaw} Collection object reference.
     * <p>
     * Makes calls to {@link #parseNextOneLineJsonElementIntoPickerEvent(InputStream)} to parse each line of
     * the {@param source}.
     *
     * @param source              {@link InputStream} to read the remaining unread bytes from.
     * @param maxEvents           - max count of events that should be read from the {@link InputStream}.
     * @param mapPickerSummaryRaw - object reference for the collection in which all the parsed {@link PickerEvent}
     *                            will be written to. The value of the entryset {@link Map.Entry} is initated to be
     *                            a {@link TreeSet} using the static {@link #articlePickComparator} for this Factory.
     * @throws IOException thrown from the {@link #parseNextOneLineJsonElementIntoPickerEvent(InputStream)} call.
     */
    private static void parseInputJsonStream(
            @WillNotClose InputStream source,
            int maxEvents,
            @Nonnull final Map<Picker, SortedSet<ArticlePick>> mapPickerSummaryRaw) throws IOException {
        for (int countEvents = 0; countEvents < maxEvents; countEvents++) {

            final PickerEvent event = parseNextOneLineJsonElementIntoPickerEvent(source);
            if (event == null) { //reach end of stream
                break;
            }
            final Article article = event.article;

            if (isTemperatureZoneInFilter(article.temperatureZone)) { //if article is for zone that we're interested in
                final Picker picker = event.picker;

                //get the map value, article picks set, of this picker, creating the set one-time if it's null
                final SortedSet<ArticlePick> setArticlePicks = mapPickerSummaryRaw.computeIfAbsent(picker, k -> new TreeSet<>(articlePickComparator));

                final String articleNameCapitalized = article.name.toUpperCase();
                final Date eventTimestamp = event.timestamp;
                setArticlePicks.add(new ArticlePick(articleNameCapitalized, eventTimestamp)); //also modifies the map value
            }
        }
    }

    /**
     * Note:
     * The {@param maxEvents} and {@param maxTime} constraints I've interpreted from the README rules
     * to apply only to each specific {@link StreamProcessor#process(InputStream, OutputStream)} call.
     * <p>
     * If on the other hand the intention was for the constraints to be for the {@link StreamProcessor}
     * returned form this as a whole (aka {@param maxEvents} constrains all events processed from this processor
     * regardless of how many {@link StreamProcessor#process(InputStream, OutputStream)} calls there were)
     * then the conditional logic can be within the {@link StreamProcessor#process(InputStream, OutputStream)}
     * implementation where simply checked are if the conditions are met. If desired give me the feedback and I can
     * refactor to reflect this behaviour.
     */
    @Override
    public StreamProcessor createProcessor(int maxEvents, @Nonnull Duration maxTime) {
        if (maxEvents < 0 || maxTime.isNegative()) {
            throw new IllegalArgumentException("Cannot have a negative maxEvents or negative maxTime Duration value passed.");
        }

        return (source, sink) -> {
            if (source == null || sink == null)
                throw new IllegalArgumentException("Invalid null object reference a Stream object passed.");

            final SortedMap<Picker, SortedSet<ArticlePick>> mapPickerSummaryRaw = new TreeMap<>(pickerComparator);

            final Future<?> future = pool.submit(() -> {

                try {
                    synchronized (source) { //lock this InputStream while reading operation is ongoing to prevent concurrent modification
                        parseInputJsonStream(source, maxEvents, mapPickerSummaryRaw);
                    }
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });

            try {
                //block current thread until end or maxTime passed
                future.get(maxTime.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException e) {
                final Throwable threadTaskThrowable = e.getCause();
                if (threadTaskThrowable instanceof UncheckedIOException)
                    throw (IOException) threadTaskThrowable.getCause();
                throw new IllegalStateException("Thread task for reading input stream either threw exception or was the thread was interrupted", threadTaskThrowable);
            } catch (TimeoutException e) {
                future.cancel(true);
            } finally {
                // continue to attempt to write the completed parsed objects into the sink in spite of
                // potentially some of the Source reading throwing any of the caught (ie. IO, TimeOut, etc) exception

                //Convert the processed collection into Json
                final String jsonProcessedCollection = convertPickerSummaryCollectionToJson(mapPickerSummaryRaw);

                //Then: write Json object to Output Sink
                sink.write(jsonProcessedCollection.getBytes());
            }

        };
    }

}

