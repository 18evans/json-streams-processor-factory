package evans18.assignment.streamprocessorfactory.model.input;

import java.util.Date;

/**
 * Describes an event of a relation between a {@link Picker} and an {@link Article}.
 * <p>
 * Used in expected input.
 */
public class PickerEvent {
    public final String id;
    public final Date timestamp;
    public final Picker picker;
    public final Article article;
    public final int quantity;

    public PickerEvent(String id, Date timestamp, Picker picker, Article article, int quantity) {
        this.id = id;
        this.timestamp = timestamp;
        this.picker = picker;
        this.article = article;
        this.quantity = quantity;
    }
}
