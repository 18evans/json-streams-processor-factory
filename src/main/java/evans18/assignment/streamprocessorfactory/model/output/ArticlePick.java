package evans18.assignment.streamprocessorfactory.model.output;

import com.google.gson.annotations.SerializedName;
import evans18.assignment.streamprocessorfactory.model.input.Article;
import evans18.assignment.streamprocessorfactory.model.input.PickerEvent;

import java.util.Date;

/**
 * Describes relation between an {@link Article} and a {@link PickerEvent}.
 * <p>
 * Used in expected output.
 */
public class ArticlePick {
    @SerializedName("article_name")
    public final String articleName;
    private final Date timestamp;

    /**
     * @param articleName from {@link Article#name}.
     * @param timestamp   from {@link PickerEvent#timestamp}.
     */
    public ArticlePick(String articleName, Date timestamp) {
        this.articleName = articleName;
        this.timestamp = timestamp;
    }

    public Date getTimestamp() {
        return timestamp;
    }
}
